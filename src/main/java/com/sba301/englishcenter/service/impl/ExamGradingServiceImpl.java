package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.request.GradeAnswerRequest;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
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

    private final ExamRepository examRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAnswerRepository answerRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final UserRepository userRepository;
    private final ExamGrader grader;
    private final ExamMapper examMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttemptSummaryResponse> getPendingAttempts(Pageable pageable) {
        UserPrincipal grader = currentPrincipal();
        Page<ExamAttempt> page = isAdmin(grader)
                ? attemptRepository.findPendingManualGrading(GRADABLE_STATUSES, QuestionType.ESSAY, pageable)
                : attemptRepository.findPendingManualGradingForMentor(GRADABLE_STATUSES, QuestionType.ESSAY,
                        ExamScope.TRIAL, grader.getUserId(), pageable);

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
        assertCanGrade(attempt.getExam());
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
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
        assertCanGrade(exam);
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
        }

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

        if (!summary.pendingManual() && attempt.getStatus() == AttemptStatus.SUBMITTED) {
            attempt.setStatus(AttemptStatus.GRADED);
        }
        attemptRepository.save(attempt);

        exam.setAverageScore(attemptRepository.averageScoreByExamId(exam.getExamId(),
                ExamAttemptServiceImpl.FINISHED_STATUSES).setScale(2, RoundingMode.HALF_UP));
        examRepository.save(exam);

        log.info("{} chấm câu {} của lượt thi {}: {} điểm (còn chờ chấm: {})", principal.getUserId(), questionId,
                attemptId, request.getScore(), summary.pendingManual());
        return examMapper.toResult(attempt, examQuestions, answers, true);
    }

    /** Admin chấm mọi đề; mentor chấm mọi đề thi thử (trial) và đề của khóa học mình phụ trách. */
    private void assertCanGrade(Exam exam) {
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
        if (!isCourseMentor) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    private Map<UUID, ExamAnswer> answersByQuestion(UUID attemptId) {
        return answerRepository.findDetailedByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getQuestionId(), Function.identity(), (a, b) -> a));
    }

    private static boolean isAdmin(UserPrincipal principal) {
        return RoleName.ADMIN.name().equals(principal.getRoleName());
    }

    private static UserPrincipal currentPrincipal() {
        return SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
