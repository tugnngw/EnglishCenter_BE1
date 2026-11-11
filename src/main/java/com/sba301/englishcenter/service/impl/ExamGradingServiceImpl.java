package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.MentoringPlanStatus;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.request.GradeAnswerRequest;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.service.ExamMapper;
import com.sba301.englishcenter.repository.ExamAnswerRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.repository.ExamRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.ExamGrader;
import com.sba301.englishcenter.service.ExamGradingService;
import com.sba301.englishcenter.service.HomeworkGraderAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamGradingServiceImpl implements ExamGradingService {

    /** Trạng thái có thể còn câu chờ chấm (expired: hệ thống tự nộp nhưng vẫn có thể có bài viết). */
    private static final EnumSet<AttemptStatus> GRADABLE_STATUSES =
            EnumSet.of(AttemptStatus.SUBMITTED, AttemptStatus.EXPIRED);

    /**
     * Trạng thái gói kèm 1-1 tính là "đã mua" → mentor 1-1 của học viên được
     * chấm phần tự luận của đề cuối kỳ (V29).
     */
    private static final EnumSet<MentoringPlanStatus> PLAN_STATUSES =
            EnumSet.of(MentoringPlanStatus.active, MentoringPlanStatus.completed);

    private final ExamRepository examRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAnswerRepository answerRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final UserRepository userRepository;
    private final ExamGrader grader;
    private final ExamMapper examMapper;
    private final NotificationService notificationService;
    /** Tra cứu mentor 1-1 của học viên (V29) — mentor đó chấm phần tự luận của đề cuối kỳ. */
    private final HomeworkGraderAssignmentService mentorAssignmentService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> getPendingAttempts(Pageable pageable) {
        UserPrincipal grader = currentPrincipal();
        Page<ExamAttempt> page = isAdmin(grader)
                ? attemptRepository.findPendingManualGrading(GRADABLE_STATUSES, QuestionType.ESSAY, pageable)
                : attemptRepository.findPendingManualGradingForMentorWithPlans(
                        GRADABLE_STATUSES, QuestionType.ESSAY, ExamScope.TRIAL,
                        grader.getUserId(), PLAN_STATUSES, pageable);

        return PageResponse.<AttemptSummaryResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream()
                        .map(a -> examMapper.toAttemptSummary(a, true, true, true))
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptResultResponse getAttemptForGrading(UUID attemptId) {
        ExamAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));
        assertCanGrade(attempt.getExam(), attempt);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
        }
        // V30: bài tự luận còn lưu nháp (học viên chưa mua gói mentor) → chưa được chấm.
        // Khi học viên nâng cấp gói, bài chuyển sang 'submitted' và mở cho mentor chấm.
        if (attempt.getStatus() == AttemptStatus.AWAITING_MENTOR) {
            throw new AppException(ErrorCode.ATTEMPT_AWAITING_MENTOR_UPGRADE);
        }
        return examMapper.toResult(attempt,
                examQuestionRepository.findDetailedByExamId(attempt.getExam().getExamId()),
                answersByQuestion(attemptId), true);
    }

    @Override
    @Transactional
    public AttemptResultResponse gradeAnswer(UUID attemptId, UUID questionId, GradeAnswerRequest request) {
        UserPrincipal principal = currentPrincipal();
        ExamAttempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));
        Exam exam = attempt.getExam();
        assertCanGrade(exam, attempt);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
        }
        // V30: bài tự luận còn lưu nháp (học viên chưa mua gói mentor) → chưa được chấm.
        if (attempt.getStatus() == AttemptStatus.AWAITING_MENTOR) {
            throw new AppException(ErrorCode.ATTEMPT_AWAITING_MENTOR_UPGRADE);
        }

        claimForGrading(attempt, principal);

        ExamAnswer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, questionId)
                .orElseThrow(() -> new AppException(ErrorCode.ANSWER_NOT_FOUND));
        if (answer.getQuestion().getQuestionType() != QuestionType.ESSAY) {
            throw new AppException(ErrorCode.QUESTION_NOT_MANUALLY_GRADABLE);
        }
        if (request.getScore().compareTo(ExamGrader.points(answer.getQuestion())) > 0) {
            throw new AppException(ErrorCode.INVALID_SCORE,
                    "Điểm tối đa của câu này là " + ExamGrader.points(answer.getQuestion()));
        }

        answer.setManualScore(request.getScore());
        answer.setMentorFeedback(request.getFeedback());
        answer.setGradedBy(userRepository.getReferenceById(principal.getUserId()));
        answer.setGradedDate(LocalDateTime.now());
        answerRepository.save(answer);

        // Tính lại tổng điểm của lượt thi
        List<ExamQuestion> examQuestions = examQuestionRepository.findDetailedByExamId(exam.getExamId());
        Map<UUID, ExamAnswer> answers = answersByQuestion(attemptId);
        answers.put(questionId, answer);
        ExamGrader.Summary summary = grader.grade(attempt, examQuestions, answers, exam.getPassingScore());
        answerRepository.saveAll(answers.values());

        boolean justGraded = !summary.pendingManual() && attempt.getStatus() == AttemptStatus.SUBMITTED;
        if (justGraded) {
            attempt.setStatus(AttemptStatus.GRADED);
        }
        attemptRepository.save(attempt);
        if (justGraded) {
            notifyGraded(attempt, exam);
        }

        exam.setAverageScore(attemptRepository.averageScoreByExamId(exam.getExamId(),
                ExamAttemptServiceImpl.FINISHED_STATUSES).setScale(2, RoundingMode.HALF_UP));
        examRepository.save(exam);

        log.info("{} chấm câu {} của lượt thi {}: {} điểm (còn chờ chấm: {})", principal.getUserId(), questionId,
                attemptId, request.getScore(), summary.pendingManual());
        return examMapper.toResult(attempt, examQuestions, answers, true);
    }

    /**
     * Báo học viên khi mentor chấm xong lượt thi. Đề Quiz / Giữa kỳ của khóa đạt thì nhắc đề tiếp theo
     * có thể đã mở (luật thi nối tiếp ở ExamAccessPolicy).
     */
    private void notifyGraded(ExamAttempt attempt, Exam exam) {
        boolean passed = Boolean.TRUE.equals(attempt.getPassed());
        StringBuilder message = new StringBuilder("Đề \"").append(exam.getExamTitle()).append("\" đã được chấm: ")
                .append(passed ? "Đạt" : "Chưa đạt").append('.');
        if (passed && exam.getExamScope() == ExamScope.COURSE) {
            if (exam.getExamType() == ExamType.QUIZ) {
                message.append(" Đạt hết Quiz của khóa là mở được đề Giữa kỳ.");
            } else if (exam.getExamType() == ExamType.MIDTERM) {
                message.append(" Đạt đủ bài tập của khóa là mở được đề Cuối kỳ.");
            }
        }
        try {
            notificationService.notify(attempt.getStudent().getUserId(), NotificationType.exam_result,
                    "Bài thi đã được chấm", message.toString());
        } catch (RuntimeException ex) {
            // Không để lỗi gửi thông báo làm hỏng kết quả chấm
            log.warn("Không gửi được thông báo chấm bài cho lượt thi {}: {}", attempt.getAttemptId(), ex.getMessage());
        }
    }

    /** Admin chấm mọi đề; mentor chấm mọi đề thi thử (trial) và đề của khóa học mình phụ trách. */
    private void assertCanGrade(Exam exam) {
        assertCanGrade(exam, null);
    }

    /**
     * Quyền chấm:
     * <ul>
     *   <li>Admin: chấm mọi đề;</li>
     *   <li>Mentor phụ trách khóa: chấm đề của khóa mình;</li>
     *   <li>Mentor 1-1 của HỌC VIÊN (V29): chấm phần tự luận của đề cuối kỳ mà
     *       học viên đó làm — vì đề này BẮT BUỘC có mentor chấm tay.</li>
     * </ul>
     *
     * @param attempt lượt thi (dùng để biết học viên là ai); null = không kiểm tra theo học viên.
     */
    private void assertCanGrade(Exam exam, ExamAttempt attempt) {
        UserPrincipal principal = currentPrincipal();
        if (isAdmin(principal)) {
            return;
        }
        boolean isMentor = RoleName.MENTOR.name().equals(principal.getRoleName());
        if (isMentor && exam.getExamScope() == ExamScope.TRIAL) {
            return;
        }
        boolean isCourseMentor = isMentor
                && exam.getCourse() != null
                && exam.getCourse().getMentor() != null
                && exam.getCourse().getMentor().getUserId().equals(principal.getUserId());
        if (isCourseMentor) {
            return;
        }
        // V29: mentor 1-1 của học viên được chấm đề cuối kỳ của học viên đó.
        if (isMentor && attempt != null && attempt.getStudent() != null) {
            UUID studentId = attempt.getStudent().getUserId();
            boolean isStudentMentor = mentorAssignmentService.findAssignedMentorId(studentId)
                    .map(id -> id.equals(principal.getUserId()))
                    .orElse(false);
            if (isStudentMentor) {
                return;
            }
        }
        throw new AppException(ErrorCode.UNAUTHORIZED);
    }

    private Map<UUID, ExamAnswer> answersByQuestion(UUID attemptId) {
        return answerRepository.findDetailedByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getQuestionId(), Function.identity(), (a, b) -> a));
    }

    /**
     * Lần chấm đầu tiên của mentor = nhận chấm bài; mentor khác chấm vào bài đã có người nhận bị chặn (5027).
     * Admin chấm được mọi bài (xử lý khi mentor nghỉ giữa chừng) và không giữ bài.
     */
    private void claimForGrading(ExamAttempt attempt, UserPrincipal principal) {
        if (isAdmin(principal)) {
            return;
        }
        User owner = attempt.getGradingBy();
        if (owner == null) {
            attempt.setGradingBy(userRepository.getReferenceById(principal.getUserId()));
            attempt.setGradingClaimedAt(LocalDateTime.now());
        } else if (!owner.getUserId().equals(principal.getUserId())) {
            throw new AppException(ErrorCode.ATTEMPT_GRADING_CLAIMED,
                    "Bài này đang được " + owner.getFullName() + " chấm");
        }
    }

    private static boolean isAdmin(UserPrincipal principal) {
        return RoleName.ADMIN.name().equals(principal.getRoleName());
    }

    private static UserPrincipal currentPrincipal() {
        return SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
