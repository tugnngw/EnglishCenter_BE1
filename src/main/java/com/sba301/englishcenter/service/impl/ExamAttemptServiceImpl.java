package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.practice.EssayAiEvaluationData;
import com.sba301.englishcenter.dto.practice.PracticeAnswerData;
import com.sba301.englishcenter.dto.request.AnswerItemRequest;
import com.sba301.englishcenter.dto.request.SaveAnswersRequest;
import com.sba301.englishcenter.dto.request.SubmitAttemptRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import com.sba301.englishcenter.dto.response.ExamDetailResponse;
import com.sba301.englishcenter.dto.response.PracticeLevelResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.ExamSection;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.QuestionOption;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.integration.ai.AiEvaluationClient;
import com.sba301.englishcenter.integration.ai.AiEvaluationResult;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.ExamAnswerRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.repository.ExamRepository;
import com.sba301.englishcenter.repository.ExamSectionRepository;
import com.sba301.englishcenter.repository.PracticeExamRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.ExamAccessPolicy;
import com.sba301.englishcenter.service.ExamAttemptService;
import com.sba301.englishcenter.service.ExamGrader;
import com.sba301.englishcenter.service.ExamMapper;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.util.JsonUtil;
import com.sba301.englishcenter.util.JsonbMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final StudentProfileRepository studentProfileRepository;
    private final PracticeExamRepository practiceExamRepository;
    private final CertificateRepository certificateRepository;
    private final AiEvaluationClient aiEvaluationClient;
    private final ScoringScaleResolver scoringScaleResolver;
    private final NotificationService notificationService;
    private final PracticeLevelCalculator practiceLevelCalculator;
    // AI chấm mất 10 - 60 giây mỗi câu: không giữ transaction / khóa dòng trong lúc chờ AI
    private final PlatformTransactionManager transactionManager;

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

        // TASK-DEV1 T5 / T7: Giới hạn 3 lượt test miễn phí cho Learner (USER role)
        User user = userRepository.findById(studentId).orElse(null);
        boolean isLearner = (user != null && user.getRole() != null && RoleName.USER.name().equalsIgnoreCase(user.getRole().getRoleName()))
                || SecurityUtils.getCurrentUserPrincipal().map(p -> RoleName.USER.name().equalsIgnoreCase(p.getRoleName())).orElse(false);
        if (isLearner) {
            long practiceAttempts = countPracticeAttempts(studentId);
            long examAttempts = attemptRepository.countByStudent_UserId(studentId);
            if (practiceAttempts + examAttempts >= 3) {
                throw new AppException(ErrorCode.PRACTICE_ATTEMPT_LIMIT);
            }
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
        return withPracticeLevel(examMapper.toResult(attempt, result.examQuestions(), result.answers(), false),
                attempt, result.examQuestions(), result.answers());
    }

    // ============================================
    // HISTORY & RESULT (D3-13, D3-14)
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> getMyAttempts(UUID examId, Pageable pageable) {
        return getMyAttempts(examId, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> getMyAttempts(UUID examId, ExamScope examScope, Pageable pageable) {
        UUID studentId = currentUserId();
        Page<ExamAttempt> page = examId != null
                ? attemptRepository.findByStudent_UserIdAndExam_ExamId(studentId, examId, pageable)
                : examScope != null
                ? attemptRepository.findByStudent_UserIdAndExam_ExamScope(studentId, examScope, pageable)
                : attemptRepository.findByStudent_UserId(studentId, pageable);

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
        Map<UUID, ExamAnswer> answers = answersByQuestion(attemptId);
        return withPracticeLevel(examMapper.toResult(attempt, examQuestions, answers, false),
                attempt, examQuestions, answers);
    }

    // ============================================
    // DEV 5: MENTOR XEM TEST LEVEL CỦA HỌC VIÊN (để tư vấn lộ trình)
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> getStudentLevelTests(UUID studentId, Pageable pageable) {
        Page<ExamAttempt> page = attemptRepository.findByStudent_UserIdAndExam_ExamScope(studentId, ExamScope.PRACTICE,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "startTime")));
        return PageResponse.<AttemptSummaryResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream()
                        .map(a -> examMapper.toAttemptSummary(a, true, hasPendingManual(a), false))
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptResultResponse getStudentLevelTestResult(UUID studentId, UUID attemptId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));
        // Chỉ lượt test level (đề luyện tập) đã nộp của đúng học viên này
        if (!attempt.getStudent().getUserId().equals(studentId)
                || attempt.getExam().getExamScope() != ExamScope.PRACTICE) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_FOUND);
        }
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
        }
        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(attempt.getExam().getExamId());
        Map<UUID, ExamAnswer> answers = answersByQuestion(attemptId);
        return withPracticeLevel(examMapper.toResult(attempt, examQuestions, answers, false),
                attempt, examQuestions, answers);
    }

    // ============================================
    // TIMEOUT SWEEPER
    // ============================================

    @Override
    @Transactional(readOnly = true)
    public boolean hasPassedExam(UUID studentId, UUID examId) {
        return attemptRepository.existsByExam_ExamIdAndStudent_UserIdAndPassedTrue(examId, studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasReachedPracticeBand(UUID studentId, UUID examId, BigDecimal targetBand) {
        List<ExamAttempt> attempts = attemptRepository
                .findByExam_ExamIdAndStudent_UserIdAndStatusIn(examId, studentId, FINISHED_STATUSES);
        if (attempts.isEmpty()) {
            return false;
        }
        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(examId);
        for (ExamAttempt attempt : attempts) {
            PracticeLevelResponse level = practiceLevelCalculator.calculate(studentId, examQuestions,
                    answersByQuestion(attempt.getAttemptId()));
            // null = còn câu Writing / Speaking chưa được AI chấm
            if (level == null) {
                continue;
            }
            if (targetBand == null || (level.getOverallScore() != null
                    && level.getOverallScore().compareTo(targetBand) >= 0)) {
                return true;
            }
        }
        return false;
    }

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
    // AI CHẤM WRITING / SPEAKING (đề luyện tập)
    // ============================================

    /** Một câu Writing / Speaking đã làm nhưng chưa có điểm. */
    private record EssayTask(UUID questionId, PracticeExamType skill, String question, String answer) {
    }

    private record EssayGrade(EssayTask task, ScoringScale scale, AiEvaluationResult result) {
    }

    @Override
    public AttemptResultResponse aiGradeAttempt(UUID attemptId) {
        UUID studentId = currentUserId();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Đọc các câu cần chấm (transaction ngắn)
        List<EssayTask> tasks = tx.execute(status -> {
            ExamAttempt attempt = findOwnedAttempt(attemptId, false);
            if (attempt.getExam().getExamScope() != ExamScope.PRACTICE) {
                throw new AppException(ErrorCode.INVALID_EXAM_SCOPE, "Chỉ đề luyện tập mới được AI chấm");
            }
            if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
                throw new AppException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
            }
            return pendingEssayTasks(attempt);
        });

        // 2. Gọi AI ngoài transaction, theo thang của chứng chỉ mục tiêu; lỗi giữa chừng vẫn lưu các câu đã chấm
        List<EssayGrade> grades = new ArrayList<>();
        AppException failure = null;
        for (EssayTask task : tasks) {
            ScoringScale scale = scoringScaleResolver.resolve(studentId, task.skill());
            try {
                grades.add(new EssayGrade(task, scale,
                        aiEvaluationClient.evaluate(task.skill(), task.question(), task.answer(), scale)));
            } catch (AppException ex) {
                failure = ex;
                break;
            }
        }

        // 3. Ghi điểm và tính lại tổng (khóa dòng attempt)
        AttemptResultResponse result = tx.execute(status -> saveAiGrades(attemptId, studentId, grades));
        if (failure != null) {
            throw failure;
        }
        return result;
    }

    private List<EssayTask> pendingEssayTasks(ExamAttempt attempt) {
        Map<UUID, ExamAnswer> answers = answersByQuestion(attempt.getAttemptId());
        List<EssayTask> tasks = new ArrayList<>();
        for (ExamQuestion eq : examQuestionRepository.findDetailedByExamId(attempt.getExam().getExamId())) {
            Question question = eq.getQuestion();
            ExamAnswer answer = answers.get(question.getQuestionId());
            if (!grader.isPendingManual(question, answer)) {
                continue;
            }
            ExamSection section = eq.getSection();
            String instructions = section != null && StringUtils.hasText(section.getInstructions())
                    ? section.getInstructions().trim() + "\n" : "";
            tasks.add(new EssayTask(question.getQuestionId(),
                    isSpeaking(section) ? PracticeExamType.SPEAKING : PracticeExamType.WRITING,
                    instructions + question.getQuestionText(), answer.getAnswerText()));
        }
        return tasks;
    }

    private static boolean isSpeaking(ExamSection section) {
        return section != null && ((section.getSectionType() != null && section.getSectionType().toUpperCase().contains("SPEAK"))
                || (section.getTitle() != null && section.getTitle().toUpperCase().contains("SPEAK")));
    }

    private AttemptResultResponse saveAiGrades(UUID attemptId, UUID studentId, List<EssayGrade> grades) {
        LocalDateTime now = LocalDateTime.now();
        ExamAttempt attempt = findOwnedAttempt(attemptId, true);
        Exam exam = attempt.getExam();
        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(exam.getExamId());
        Map<UUID, ExamAnswer> answers = answersByQuestion(attemptId);

        int saved = 0;
        for (EssayGrade grade : grades) {
            ExamAnswer answer = answers.get(grade.task().questionId());
            // Request khác đã chấm câu này trước -> giữ kết quả đó
            if (answer == null || answer.getManualScore() != null) {
                continue;
            }
            AiEvaluationResult r = grade.result();
            ScoringScale scale = grade.scale();
            answer.setManualScore(bandToPoints(r.bandScore(), scale, ExamGrader.points(answer.getQuestion())));
            answer.setMentorFeedback(r.feedback());
            answer.setAiEvaluation(JsonbMapper.write(new EssayAiEvaluationData(grade.task().skill().name(),
                    r.bandScore(), r.cefrLevel(), scale.certificateName(), scale.minScore(), scale.maxScore(),
                    r.taskAchievement(), r.coherenceCohesion(), r.lexicalResource(), r.grammaticalAccuracy(),
                    r.feedback(), r.suggestions(), r.model(), now, r.weaknesses(), r.pronunciationIssues())));
            answer.setGradedDate(now);
            saved++;
        }

        ExamGrader.Summary summary = grader.grade(attempt, examQuestions, answers, exam.getPassingScore());
        answerRepository.saveAll(answers.values());
        if (!summary.pendingManual() && attempt.getStatus() == AttemptStatus.SUBMITTED) {
            attempt.setStatus(AttemptStatus.GRADED);
        }
        attemptRepository.save(attempt);
        exam.setAverageScore(attemptRepository.averageScoreByExamId(exam.getExamId(), FINISHED_STATUSES)
                .setScale(2, RoundingMode.HALF_UP));
        examRepository.save(exam);

        if (saved > 0 && !summary.pendingManual()) {
            updateStudentProfileOnAttemptCompletion(attempt, summary, examQuestions, answers);
            notificationService.notify(studentId, NotificationType.exam_result, "AI đã chấm xong bài luyện tập",
                    "Bài \"" + exam.getExamTitle() + "\" đạt " + summary.score().toPlainString() + "/"
                            + summary.maxScore().toPlainString() + " điểm (" + summary.percentage().toPlainString() + "%).");
        }
        log.info("AI chấm {} câu Writing/Speaking của lượt thi {} (còn chờ chấm: {})", saved, attemptId,
                summary.pendingManual());
        return withPracticeLevel(examMapper.toResult(attempt, examQuestions, answers, false),
                attempt, examQuestions, answers);
    }

    /** Đề luyện tập đã chấm xong: kèm band / CEFR theo chứng chỉ mục tiêu (giống giá trị lưu vào hồ sơ). */
    private AttemptResultResponse withPracticeLevel(AttemptResultResponse response, ExamAttempt attempt,
                                                    List<ExamQuestion> examQuestions, Map<UUID, ExamAnswer> answers) {
        if (attempt.getExam().getExamScope() == ExamScope.PRACTICE) {
            response.setPracticeLevel(practiceLevelCalculator.calculate(attempt.getStudent().getUserId(),
                    examQuestions, answers));
        }
        return response;
    }

    /** Band theo thang chứng chỉ -> điểm của câu: points * (band - min) / (max - min). */
    static BigDecimal bandToPoints(BigDecimal band, ScoringScale scale, BigDecimal points) {
        BigDecimal range = scale.maxScore().subtract(scale.minScore());
        if (band == null || range.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal ratio = band.subtract(scale.minScore()).divide(range, 6, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO).min(BigDecimal.ONE);
        return points.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
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

        // TASK-DEV1 T7: Cập nhật current_level và current_score vào StudentProfile khi hoàn thành placement_test / trial
        if (!summary.pendingManual() && (exam.getExamType() == ExamType.placement_test
                || exam.getExamScope() == ExamScope.TRIAL || exam.getExamScope() == ExamScope.PRACTICE)) {
            updateStudentProfileOnAttemptCompletion(attempt, summary, examQuestions, answers);
        }

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

    private long countPracticeAttempts(UUID studentId) {
        try {
            String filter = JsonbMapper.write(List.of(Map.of("studentId", studentId.toString())));
            return practiceExamRepository.findByAnswersContaining(filter).stream()
                    .flatMap(exam -> JsonbMapper.readList(exam.getAnswers(), new TypeReference<List<PracticeAnswerData>>() {}).stream())
                    .filter(a -> studentId.equals(a.studentId()))
                    .count();
        } catch (Exception ex) {
            log.warn("Error counting practice attempts for student {}: {}", studentId, ex.getMessage());
            return 0;
        }
    }

    private void updateStudentProfileOnAttemptCompletion(ExamAttempt attempt, ExamGrader.Summary summary,
                                                         List<ExamQuestion> examQuestions,
                                                         Map<UUID, ExamAnswer> answers) {
        if (summary.score() == null || summary.maxScore() == null || summary.maxScore().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        UUID studentId = attempt.getStudent().getUserId();
        StudentProfile profile = studentProfileRepository.findById(studentId)
                .orElseGet(() -> StudentProfile.builder().user(userRepository.getReferenceById(studentId)).build());

        // Đề luyện tập: lưu đúng band / CEFR hiển thị ở trang kết quả (theo từng kỹ năng, thang chứng chỉ mục tiêu)
        if (attempt.getExam().getExamScope() == ExamScope.PRACTICE) {
            PracticeLevelResponse level = practiceLevelCalculator.calculate(studentId, examQuestions, answers);
            if (level != null) {
                profile.setCurrentLevel(level.getCefrLevel());
                profile.setCurrentScore(level.getOverallScore());
                profile.setLastUpdated(LocalDateTime.now());
                studentProfileRepository.save(profile);
                log.info("Updated student profile for {} from practice: level={}, score={}", studentId,
                        level.getCefrLevel(), level.getOverallScore());
                return;
            }
        }

        BigDecimal percentage = summary.percentage();
        if (percentage == null) {
            percentage = summary.score().multiply(BigDecimal.valueOf(100))
                    .divide(summary.maxScore(), 2, RoundingMode.HALF_UP);
        }

        String cefr = mapPercentageToCefr(percentage);
        profile.setCurrentLevel(cefr);

        BigDecimal convertedScore = calculateScaledScore(profile.getTargetCertificateId(), percentage, summary.score());
        profile.setCurrentScore(convertedScore);
        profile.setLastUpdated(LocalDateTime.now());
        studentProfileRepository.save(profile);
        log.info("Updated student profile for {}: level={}, score={}", studentId, cefr, convertedScore);
    }

    private String mapPercentageToCefr(BigDecimal percentage) {
        double p = percentage.doubleValue();
        if (p >= 90.0) return "C2";
        if (p >= 80.0) return "C1";
        if (p >= 65.0) return "B2";
        if (p >= 50.0) return "B1";
        if (p >= 35.0) return "A2";
        return "A1";
    }

    private BigDecimal calculateScaledScore(UUID targetCertId, BigDecimal percentage, BigDecimal rawScore) {
        if (targetCertId != null) {
            Optional<Certificate> certOpt = certificateRepository.findById(targetCertId);
            if (certOpt.isPresent()) {
                String certName = certOpt.get().getCertificateName().toUpperCase();
                double p = percentage.doubleValue() / 100.0;
                if (certName.contains("IELTS")) {
                    double ielts = Math.round(p * 9.0 * 2.0) / 2.0;
                    return BigDecimal.valueOf(Math.max(1.0, Math.min(9.0, ielts))).setScale(1, RoundingMode.HALF_UP);
                } else if (certName.contains("TOEIC")) {
                    long toeic = Math.round((p * 980.0 + 10.0) / 5.0) * 5;
                    return BigDecimal.valueOf(Math.max(10, Math.min(990, toeic))).setScale(0, RoundingMode.HALF_UP);
                } else if (certName.contains("VSTEP")) {
                    double vstep = Math.round(p * 10.0 * 2.0) / 2.0;
                    return BigDecimal.valueOf(Math.max(0.0, Math.min(10.0, vstep))).setScale(1, RoundingMode.HALF_UP);
                }
            }
        }
        return rawScore.setScale(1, RoundingMode.HALF_UP);
    }
}
