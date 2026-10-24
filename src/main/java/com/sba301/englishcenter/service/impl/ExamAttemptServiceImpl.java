package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.dto.request.AnswerItemRequest;
import com.sba301.englishcenter.dto.request.SaveAnswersRequest;
import com.sba301.englishcenter.dto.request.SubmitAttemptRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import com.sba301.englishcenter.dto.response.ExamDetailResponse;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.QuestionOption;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.service.ExamMapper;
import com.sba301.englishcenter.repository.ExamAnswerRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.repository.ExamRepository;
import com.sba301.englishcenter.repository.ExamSectionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.ExamAccessPolicy;
import com.sba301.englishcenter.service.ExamAttemptService;
import com.sba301.englishcenter.service.ExamGrader;
import com.sba301.englishcenter.util.JsonUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Luồng làm bài của học viên.
 * <p>
 * An toàn đồng thời (D3-16):
 * <ul>
 *     <li>Bắt đầu lượt thi: khóa advisory theo (học viên, đề) + unique index {@code uq_exam_attempt_in_progress}
 *     và {@code uq_exam_attempt_number} nên không thể mở 2 lượt song song hoặc vượt max_attempts;</li>
 *     <li>Lưu đáp án / nộp bài: {@code SELECT ... FOR UPDATE} trên dòng attempt nên nộp trùng được tuần tự hóa,
 *     lần thứ hai thấy status != in_progress và bị từ chối {@code ATTEMPT_ALREADY_SUBMITTED}.</li>
 * </ul>
 * Hết giờ: quá {@code expires_at + submit-grace-seconds} thì lượt thi bị hệ thống tự nộp (status expired) với
 * các câu đã lưu. Các method dùng {@code noRollbackFor = AppException.class} để việc tự nộp vẫn được lưu dù
 * request trả về lỗi {@code ATTEMPT_TIME_EXPIRED}; vì vậy mọi kiểm tra đầu vào phải chạy trước khi ghi DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamAttemptServiceImpl implements ExamAttemptService {

    static final EnumSet<AttemptStatus> FINISHED_STATUSES =
            EnumSet.of(AttemptStatus.SUBMITTED, AttemptStatus.GRADED, AttemptStatus.EXPIRED);

    private static final int SWEEP_BATCH_SIZE = 100;

    private final ExamRepository examRepository;
    private final ExamSectionRepository sectionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAnswerRepository answerRepository;
    private final UserRepository userRepository;
    private final ExamAccessPolicy accessPolicy;
    private final ExamGrader grader;
    private final ExamMapper examMapper;

    @PersistenceContext
    private EntityManager entityManager;

    /** Thời gian ân hạn sau expires_at cho độ trễ mạng khi nộp bài. */
    @Value("${exam.attempt.submit-grace-seconds:30}")
    private long graceSeconds = 30;

    // ============================================
    // START (D3-08)
    // ============================================

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public AttemptDetailResponse startAttempt(UUID examId) {
        UUID studentId = currentUserId();
        LocalDateTime now = LocalDateTime.now();
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new AppException(ErrorCode.EXAM_NOT_FOUND));
        accessPolicy.assertCanAttempt(studentId, exam, now);

        lockStudentExam(studentId, examId);

        Optional<ExamAttempt> inProgress = attemptRepository
                .findFirstByExam_ExamIdAndStudent_UserIdAndStatus(examId, studentId, AttemptStatus.IN_PROGRESS);
        if (inProgress.isPresent()) {
            ExamAttempt existing = inProgress.get();
            if (!isOverdue(existing, now)) {
                log.info("Học viên {} tiếp tục lượt thi {} của đề {}", studentId, existing.getAttemptId(), examId);
                return toInProgressDetail(existing, now);
            }
            finalizeAttempt(existing, true, now);
        }

        long used = attemptRepository.countByExam_ExamIdAndStudent_UserId(examId, studentId);
        if (exam.getMaxAttempts() != null && exam.getMaxAttempts() > 0 && used >= exam.getMaxAttempts()) {
            throw new AppException(ErrorCode.EXAM_MAX_ATTEMPTS_REACHED);
        }

        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(examId);
        if (examQuestions.isEmpty()) {
            throw new AppException(ErrorCode.EXAM_NOT_AVAILABLE, "Đề thi chưa có câu hỏi");
        }

        ExamAttempt attempt = ExamAttempt.builder()
                .exam(exam)
                .student(userRepository.getReferenceById(studentId))
                .attemptNumber(attemptRepository.findMaxAttemptNumber(examId, studentId) + 1)
                .startTime(now)
                .expiresAt(now.plusMinutes(exam.getDurationMinutes()))
                .maxScore(examQuestions.stream().map(eq -> ExamGrader.points(eq.getQuestion()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .status(AttemptStatus.IN_PROGRESS)
                .build();
        attempt = attemptRepository.save(attempt);

        exam.setTotalAttempts((exam.getTotalAttempts() == null ? 0 : exam.getTotalAttempts()) + 1);
        examRepository.save(exam);

        log.info("Học viên {} bắt đầu lượt thi #{} ({}) của đề {}, hạn {}", studentId, attempt.getAttemptNumber(),
                attempt.getAttemptId(), examId, attempt.getExpiresAt());
        return toInProgressDetail(attempt, examQuestions, List.of(), now);
    }

    // ============================================
    // GET / SAVE ANSWERS (D3-09)
    // ============================================

    @Override
    @Transactional
    public AttemptDetailResponse getAttempt(UUID attemptId) {
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = findOwnedAttempt(attemptId, false);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS && isOverdue(attempt, now)) {
            entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
            if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
                finalizeAttempt(attempt, true, now);
            }
        }
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            return toInProgressDetail(attempt, now);
        }
        // Đã nộp: không trả lại đề, frontend chuyển sang trang kết quả
        return examMapper.toAttemptDetail(attempt, null, List.of(), now);
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public AttemptDetailResponse saveAnswers(UUID attemptId, SaveAnswersRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = findOwnedAttempt(attemptId, true);
        assertWritable(attempt, now);

        applyAnswers(attempt, request.getAnswers(), now);

        List<ExamAnswer> saved = answerRepository.findDetailedByAttemptId(attemptId);
        return examMapper.toAttemptDetail(attempt, null, saved, now);
    }

    // ============================================
    // SUBMIT (D3-10, D3-16)
    // ============================================

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public AttemptResultResponse submitAttempt(UUID attemptId, SubmitAttemptRequest request) {
        LocalDateTime now = LocalDateTime.now();
        // Khóa dòng: hai request nộp đồng thời sẽ chờ nhau, request sau thấy status đã đổi
        ExamAttempt attempt = findOwnedAttempt(attemptId, true);
        assertWritable(attempt, now);

        if (request != null && request.getAnswers() != null && !request.getAnswers().isEmpty()) {
            applyAnswers(attempt, request.getAnswers(), now);
        }
        FinalizeResult result = finalizeAttempt(attempt, false, now);
        log.info("Học viên nộp lượt thi {}: {}/{} điểm, trạng thái {}", attemptId, attempt.getScore(),
                attempt.getMaxScore(), attempt.getStatus());
        return examMapper.toResult(attempt, result.examQuestions(), result.answers(), false);
    }

    // ============================================
    // HISTORY & RESULT (D3-13, D3-14)
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> getMyAttempts(UUID examId, Pageable pageable) {
        UUID studentId = currentUserId();
        Page<ExamAttempt> page = examId == null
                ? attemptRepository.findByStudent_UserId(studentId, pageable)
                : attemptRepository.findByStudent_UserIdAndExam_ExamId(studentId, examId, pageable);

        return PageResponse.<AttemptSummaryResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream()
                        .map(a -> examMapper.toAttemptSummary(a, !Boolean.FALSE.equals(a.getExam().getShowScore()),
                                hasPendingManual(a), false))
                        .toList())
                .build();
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public AttemptResultResponse getMyAttemptResult(UUID attemptId) {
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = findOwnedAttempt(attemptId, false);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            if (!isOverdue(attempt, now)) {
                throw new AppException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
            }
            entityManager.refresh(attempt, LockModeType.PESSIMISTIC_WRITE);
            if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
                finalizeAttempt(attempt, true, now);
            }
        }
        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(attempt.getExam().getExamId());
        return examMapper.toResult(attempt, examQuestions, answersByQuestion(attemptId), false);
    }

    // ============================================
    // TIMEOUT SWEEPER
    // ============================================

    @Override
    @Transactional
    public int autoCloseOverdueAttempts() {
        LocalDateTime now = LocalDateTime.now();
        List<UUID> ids = attemptRepository.findOverdueAttemptIds(AttemptStatus.IN_PROGRESS,
                now.minusSeconds(graceSeconds), PageRequest.of(0, SWEEP_BATCH_SIZE));
        int closed = 0;
        for (UUID id : ids) {
            Optional<ExamAttempt> locked = attemptRepository.findByIdForUpdate(id);
            if (locked.isPresent() && locked.get().getStatus() == AttemptStatus.IN_PROGRESS) {
                finalizeAttempt(locked.get(), true, now);
                closed++;
            }
        }
        if (closed > 0) {
            log.info("Đã tự nộp {} lượt thi quá hạn", closed);
        }
        return closed;
    }

    // ============================================
    // CORE
    // ============================================

    record FinalizeResult(List<ExamQuestion> examQuestions, Map<UUID, ExamAnswer> answers,
                          ExamGrader.Summary summary) {
    }

    /**
     * Khóa lượt thi lại và chấm điểm.
     *
     * @param auto true nếu hệ thống tự nộp do hết giờ
     */
    FinalizeResult finalizeAttempt(ExamAttempt attempt, boolean auto, LocalDateTime now) {
        Exam exam = attempt.getExam();
        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(exam.getExamId());
        Map<UUID, ExamAnswer> answers = answersByQuestion(attempt.getAttemptId());

        ExamGrader.Summary summary = grader.grade(attempt, examQuestions, answers, exam.getPassingScore());
        answerRepository.saveAll(answers.values());

        LocalDateTime end = auto && attempt.getExpiresAt() != null && attempt.getExpiresAt().isBefore(now)
                ? attempt.getExpiresAt() : now;
        attempt.setEndTime(end);
        attempt.setTimeSpentSeconds((int) Math.max(0, Duration.between(attempt.getStartTime(), end).getSeconds()));
        attempt.setAutoSubmitted(auto);
        attempt.setStatus(auto ? AttemptStatus.EXPIRED
                : summary.pendingManual() ? AttemptStatus.SUBMITTED : AttemptStatus.GRADED);
        attemptRepository.save(attempt);

        exam.setAverageScore(attemptRepository.averageScoreByExamId(exam.getExamId(), FINISHED_STATUSES)
                .setScale(2, RoundingMode.HALF_UP));
        examRepository.save(exam);

        if (auto) {
            log.info("Lượt thi {} hết giờ, hệ thống tự nộp", attempt.getAttemptId());
        }
        return new FinalizeResult(examQuestions, answers, summary);
    }

    /** Validate toàn bộ trước, sau đó mới ghi (vì transaction không rollback khi có AppException). */
    private void applyAnswers(ExamAttempt attempt, List<AnswerItemRequest> items, LocalDateTime now) {
        Map<UUID, Question> questions = examQuestionRepository.findDetailedByExamId(attempt.getExam().getExamId())
                .stream()
                .collect(Collectors.toMap(eq -> eq.getQuestion().getQuestionId(), ExamQuestion::getQuestion));

        // Nếu một câu xuất hiện nhiều lần trong request thì lấy giá trị cuối
        Map<UUID, AnswerItemRequest> latest = new LinkedHashMap<>();
        Map<UUID, QuestionOption> selectedOptions = new HashMap<>();
        for (AnswerItemRequest item : items) {
            Question question = questions.get(item.getQuestionId());
            if (question == null) {
                throw new AppException(ErrorCode.INVALID_ANSWER,
                        "Câu hỏi " + item.getQuestionId() + " không thuộc đề thi này");
            }
            selectedOptions.put(item.getQuestionId(), validateItem(question, item));
            latest.put(item.getQuestionId(), item);
        }

        Map<UUID, ExamAnswer> existing = answersByQuestion(attempt.getAttemptId());
        for (AnswerItemRequest item : latest.values()) {
            Question question = questions.get(item.getQuestionId());
            ExamAnswer answer = existing.get(item.getQuestionId());
            if (answer == null) {
                answer = ExamAnswer.builder().attempt(attempt).question(question).build();
            }
            answer.setSelectedOption(selectedOptions.get(item.getQuestionId()));
            answer.setAnswerText(question.getQuestionType() == QuestionType.MULTIPLE_CHOICE
                    || question.getQuestionType() == QuestionType.TRUE_FALSE ? null : item.getAnswerText());
            if (item.getTimeSpentSeconds() != null) {
                answer.setTimeSpentSeconds(item.getTimeSpentSeconds());
            }
            answer.setAnsweredDate(now);
            answerRepository.save(answer);
        }
    }

    /** @return lựa chọn được chọn (MC/TF) hoặc null. */
    private QuestionOption validateItem(Question question, AnswerItemRequest item) {
        QuestionType type = question.getQuestionType();
        if (type == QuestionType.MULTIPLE_CHOICE || type == QuestionType.TRUE_FALSE) {
            if (item.getSelectedOptionId() == null) {
                return null;
            }
            return question.getOptions().stream()
                    .filter(o -> o.getOptionId().equals(item.getSelectedOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.INVALID_ANSWER,
                            "Lựa chọn không thuộc câu hỏi " + question.getQuestionId()));
        }
        if (item.getSelectedOptionId() != null) {
            throw new AppException(ErrorCode.INVALID_ANSWER,
                    "Câu hỏi " + question.getQuestionId() + " không phải trắc nghiệm, hãy gửi answerText");
        }
        if (type == QuestionType.MATCHING && item.getAnswerText() != null && !item.getAnswerText().isBlank()) {
            var node = JsonUtil.parse(item.getAnswerText());
            if (node == null || !node.isObject()) {
                throw new AppException(ErrorCode.INVALID_ANSWER,
                        "Câu nối phải gửi answerText dạng JSON object {\"vế trái\": \"vế phải\"}");
            }
        }
        return null;
    }

    private void assertWritable(ExamAttempt attempt, LocalDateTime now) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.ATTEMPT_ALREADY_SUBMITTED);
        }
        if (isOverdue(attempt, now)) {
            finalizeAttempt(attempt, true, now);
            throw new AppException(ErrorCode.ATTEMPT_TIME_EXPIRED);
        }
    }

    boolean isOverdue(ExamAttempt attempt, LocalDateTime now) {
        return attempt.getExpiresAt() != null && now.isAfter(attempt.getExpiresAt().plusSeconds(graceSeconds));
    }

    private boolean hasPendingManual(ExamAttempt attempt) {
        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            return true;
        }
        if (attempt.getStatus() != AttemptStatus.EXPIRED) {
            return false;
        }
        return answerRepository.findDetailedByAttemptId(attempt.getAttemptId()).stream()
                .anyMatch(a -> grader.isPendingManual(a.getQuestion(), a));
    }

    private ExamAttempt findOwnedAttempt(UUID attemptId, boolean forUpdate) {
        UUID studentId = currentUserId();
        ExamAttempt attempt = (forUpdate ? attemptRepository.findByIdForUpdate(attemptId)
                : attemptRepository.findById(attemptId))
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));
        // Không tiết lộ lượt thi của người khác: trả 404 thay vì 403
        if (!attempt.getStudent().getUserId().equals(studentId)) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_FOUND);
        }
        return attempt;
    }

    private Map<UUID, ExamAnswer> answersByQuestion(UUID attemptId) {
        return answerRepository.findDetailedByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getQuestionId(), Function.identity(), (a, b) -> a));
    }

    private AttemptDetailResponse toInProgressDetail(ExamAttempt attempt, LocalDateTime now) {
        return toInProgressDetail(attempt,
                examQuestionRepository.findDetailedByExamId(attempt.getExam().getExamId()),
                answerRepository.findDetailedByAttemptId(attempt.getAttemptId()), now);
    }

    private AttemptDetailResponse toInProgressDetail(ExamAttempt attempt, List<ExamQuestion> examQuestions,
                                                     List<ExamAnswer> answers, LocalDateTime now) {
        Exam exam = attempt.getExam();
        ExamDetailResponse examDetail = examMapper.toDetail(exam,
                sectionRepository.findByExam_ExamIdOrderBySectionOrderAscCreatedAtAsc(exam.getExamId()),
                examQuestions, ExamMapper.View.ATTEMPT, true, null);
        return examMapper.toAttemptDetail(attempt, examDetail, answers, now);
    }

    /**
     * Tuần tự hóa việc bắt đầu lượt thi của cùng (học viên, đề) bằng PostgreSQL advisory lock
     * (tự nhả khi transaction kết thúc).
     */
    private void lockStudentExam(UUID studentId, UUID examId) {
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(hashtext(:lockKey))")
                .setParameter("lockKey", "exam-attempt:" + studentId + ":" + examId)
                .getSingleResult();
    }

    private static UUID currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
