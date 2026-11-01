package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.dto.practice.CriterionScoreData;
import com.sba301.englishcenter.dto.request.AnswerItemRequest;
import com.sba301.englishcenter.dto.request.SaveAnswersRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.ExamSection;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exam.ExamTestData;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.service.ExamMapper;
import com.sba301.englishcenter.repository.ExamAnswerRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.repository.ExamRepository;
import com.sba301.englishcenter.repository.ExamSectionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.integration.ai.AiEvaluationClient;
import com.sba301.englishcenter.integration.ai.AiEvaluationResult;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.PracticeExamRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.ExamAccessPolicy;
import com.sba301.englishcenter.service.ExamGrader;
import com.sba301.englishcenter.util.SessionEnrollmentChecker;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.sba301.englishcenter.exam.ExamTestData.correctOption;
import static com.sba301.englishcenter.exam.ExamTestData.question;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test luồng làm bài: hết giờ, nộp trùng, lượt thi không hợp lệ, giới hạn lượt thi, quyền truy cập.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamAttemptServiceImplTest {

    @Mock private ExamRepository examRepository;
    @Mock private ExamSectionRepository sectionRepository;
    @Mock private ExamQuestionRepository examQuestionRepository;
    @Mock private ExamAttemptRepository attemptRepository;
    @Mock private ExamAnswerRepository answerRepository;
    @Mock private UserRepository userRepository;
    @Mock private SessionEnrollmentChecker enrollmentChecker;
    @Mock private AiEvaluationClient aiEvaluationClient;
    @Mock private ScoringScaleResolver scoringScaleResolver;

    private ExamAttemptServiceImpl service;

    private final UUID studentId = UUID.randomUUID();
    private User student;
    private Exam exam;
    private Question mc;
    private Question fillBlank;
    private List<ExamQuestion> examQuestions;
    private final List<ExamAnswer> answerStore = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ExamGrader grader = new ExamGrader();
        ExamAccessPolicy policy = new ExamAccessPolicy(enrollmentChecker);
        service = new ExamAttemptServiceImpl(examRepository, sectionRepository, examQuestionRepository,
                attemptRepository, answerRepository, userRepository, policy, grader, new ExamMapper(grader),
                mock(StudentProfileRepository.class), mock(PracticeExamRepository.class),
                mock(CertificateRepository.class), aiEvaluationClient, scoringScaleResolver,
                mock(NotificationService.class), new PracticeLevelCalculator(scoringScaleResolver, grader),
                mock(PlatformTransactionManager.class));
        ReflectionTestUtils.setField(service, "entityManager", mock(EntityManager.class, Answers.RETURNS_DEEP_STUBS));

        student = ExamTestData.user(studentId);
        exam = ExamTestData.publishedExam();
        mc = question(QuestionType.MULTIPLE_CHOICE, "1", "*A", "B");
        fillBlank = question(QuestionType.FILL_BLANK, "2", "went");
        examQuestions = ExamTestData.examQuestions(exam, mc, fillBlank);

        when(examRepository.findById(exam.getExamId())).thenReturn(Optional.of(exam));
        when(examQuestionRepository.findDetailedByExamId(exam.getExamId())).thenReturn(examQuestions);
        when(sectionRepository.findByExam_ExamIdOrderBySectionOrderAscCreatedAtAsc(any())).thenReturn(List.of());
        when(userRepository.getReferenceById(studentId)).thenReturn(student);
        when(attemptRepository.averageScoreByExamId(any(), any())).thenReturn(BigDecimal.ONE);
        when(attemptRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerRepository.findDetailedByAttemptId(any())).thenAnswer(inv -> new ArrayList<>(answerStore));
        when(answerRepository.save(any())).thenAnswer(inv -> {
            ExamAnswer a = inv.getArgument(0);
            if (!answerStore.contains(a)) {
                answerStore.add(a);
            }
            return a;
        });
        when(answerRepository.saveAll(any())).thenAnswer(inv -> new ArrayList<>((java.util.Collection<?>) inv.getArgument(0)));

        loginAs(studentId, "STUDENT");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(UUID userId, String role) {
        UserPrincipal principal = UserPrincipal.builder().userId(userId).email(userId + "@test.local")
                .roleName(role).active(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role))).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private ExamAttempt givenAttempt(LocalDateTime start, LocalDateTime expires) {
        ExamAttempt attempt = ExamTestData.inProgressAttempt(exam, student, start, expires);
        attempt.setMaxScore(new BigDecimal("3"));
        when(attemptRepository.findByIdForUpdate(attempt.getAttemptId())).thenReturn(Optional.of(attempt));
        when(attemptRepository.findById(attempt.getAttemptId())).thenReturn(Optional.of(attempt));
        return attempt;
    }

    private static AnswerItemRequest item(UUID questionId, UUID optionId, String text) {
        return AnswerItemRequest.builder().questionId(questionId).selectedOptionId(optionId).answerText(text).build();
    }

    private static ErrorCode errorOf(Throwable ex) {
        return ((AppException) ex).getErrorCode();
    }

    // ------------------------------------------------------------------ start

    @Test
    void start_createsAttemptWithDeadline() {
        when(attemptRepository.findFirstByExam_ExamIdAndStudent_UserIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(attemptRepository.countByExam_ExamIdAndStudent_UserId(any(), any())).thenReturn(0L);

        AttemptDetailResponse response = service.startAttempt(exam.getExamId());

        assertThat(response.getStatus()).isEqualTo(AttemptStatus.IN_PROGRESS);
        assertThat(response.getAttemptNumber()).isEqualTo(1);
        assertThat(response.getRemainingSeconds()).isBetween(29 * 60L, 30 * 60L);
        assertThat(response.getExam().getUnsectionedQuestions()).hasSize(2);
        // Không lộ đáp án đúng khi đang làm bài
        assertThat(response.getExam().getUnsectionedQuestions().get(0).getOptions())
                .allSatisfy(o -> assertThat(o.getIsCorrect()).isNull());
        assertThat(response.getExam().getUnsectionedQuestions().get(1).getOptions()).isNull();
        assertThat(exam.getTotalAttempts()).isEqualTo(1);
    }

    @Test
    void start_resumesExistingInProgressAttempt() {
        ExamAttempt existing = givenAttempt(LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(25));
        when(attemptRepository.findFirstByExam_ExamIdAndStudent_UserIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.of(existing));

        AttemptDetailResponse response = service.startAttempt(exam.getExamId());

        assertThat(response.getAttemptId()).isEqualTo(existing.getAttemptId());
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void start_rejectedWhenMaxAttemptsReached() {
        when(attemptRepository.findFirstByExam_ExamIdAndStudent_UserIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(attemptRepository.countByExam_ExamIdAndStudent_UserId(any(), any())).thenReturn(2L);

        assertThatThrownBy(() -> service.startAttempt(exam.getExamId()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.EXAM_MAX_ATTEMPTS_REACHED));
    }

    @Test
    void start_courseExamRequiresEnrollment() {
        exam.setExamScope(com.sba301.englishcenter.common.enums.ExamScope.COURSE);
        exam.setCourse(com.sba301.englishcenter.entity.Course.builder().courseId(UUID.randomUUID()).build());
        when(enrollmentChecker.isEnrolled(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.startAttempt(exam.getExamId()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.EXAM_ENROLLMENT_REQUIRED));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void start_draftExamNotAvailable() {
        exam.setStatus(com.sba301.englishcenter.common.enums.ExamStatus.DRAFT);

        assertThatThrownBy(() -> service.startAttempt(exam.getExamId()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.EXAM_NOT_AVAILABLE));
    }

    // ------------------------------------------------------------------ submit

    @Test
    void submit_gradesObjectiveQuestions() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(20));
        service.saveAnswers(attempt.getAttemptId(), SaveAnswersRequest.builder().answers(List.of(
                item(mc.getQuestionId(), correctOption(mc).getOptionId(), null),
                item(fillBlank.getQuestionId(), null, "Went"))).build());

        AttemptResultResponse result = service.submitAttempt(attempt.getAttemptId(), null);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getScore()).isEqualByComparingTo("3");
        assertThat(attempt.getPassed()).isTrue();
        assertThat(attempt.getAutoSubmitted()).isFalse();
        assertThat(result.getAttempt().getPercentage()).isEqualByComparingTo("100.00");
        assertThat(result.getAnswers()).allSatisfy(a -> assertThat(a.getCorrect()).isTrue());
    }

    @Test
    void submit_twiceIsRejectedAndGradedOnlyOnce() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now().minusMinutes(10), LocalDateTime.now().plusMinutes(20));

        service.submitAttempt(attempt.getAttemptId(), null);
        BigDecimal scoreAfterFirst = attempt.getScore();

        assertThatThrownBy(() -> service.submitAttempt(attempt.getAttemptId(), null))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.ATTEMPT_ALREADY_SUBMITTED));
        assertThat(attempt.getScore()).isEqualTo(scoreAfterFirst);
        verify(attemptRepository, times(1)).save(attempt);
    }

    @Test
    void submit_afterTimeoutAutoSubmitsWithSavedAnswers() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now().minusMinutes(40), LocalDateTime.now().minusMinutes(10));
        answerStore.add(ExamTestData.answer(attempt, mc, correctOption(mc), null));

        assertThatThrownBy(() -> service.submitAttempt(attempt.getAttemptId(), null))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.ATTEMPT_TIME_EXPIRED));

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
        assertThat(attempt.getAutoSubmitted()).isTrue();
        assertThat(attempt.getEndTime()).isEqualTo(attempt.getExpiresAt());
        assertThat(attempt.getScore()).isEqualByComparingTo("1");
        assertThat(attempt.getTimeSpentSeconds()).isEqualTo(30 * 60);
    }

    @Test
    void submit_withinGracePeriodIsAccepted() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now().minusMinutes(30), LocalDateTime.now().minusSeconds(10));

        service.submitAttempt(attempt.getAttemptId(), null);

        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(attempt.getAutoSubmitted()).isFalse();
    }

    @Test
    void saveAnswers_afterTimeoutRejected() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now().minusMinutes(40), LocalDateTime.now().minusMinutes(5));

        assertThatThrownBy(() -> service.saveAnswers(attempt.getAttemptId(), SaveAnswersRequest.builder()
                .answers(List.of(item(fillBlank.getQuestionId(), null, "went"))).build()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.ATTEMPT_TIME_EXPIRED));
        assertThat(answerStore).isEmpty();
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
    }

    // ------------------------------------------------------------------ invalid attempts / answers

    @Test
    void attemptOfAnotherStudentIsNotFound() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now(), LocalDateTime.now().plusMinutes(30));
        loginAs(UUID.randomUUID(), "STUDENT");

        assertThatThrownBy(() -> service.submitAttempt(attempt.getAttemptId(), null))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.ATTEMPT_NOT_FOUND));
        assertThatThrownBy(() -> service.getAttempt(attempt.getAttemptId()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.ATTEMPT_NOT_FOUND));
    }

    @Test
    void unknownAttemptIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(attemptRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitAttempt(unknown, null))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.ATTEMPT_NOT_FOUND));
    }

    @Test
    void saveAnswers_rejectsQuestionOutsideExamAndForeignOption() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now(), LocalDateTime.now().plusMinutes(30));
        Question other = question(QuestionType.MULTIPLE_CHOICE, "1", "*X", "Y");

        assertThatThrownBy(() -> service.saveAnswers(attempt.getAttemptId(), SaveAnswersRequest.builder()
                .answers(List.of(item(mc.getQuestionId(), correctOption(mc).getOptionId(), null),
                        item(other.getQuestionId(), correctOption(other).getOptionId(), null))).build()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.INVALID_ANSWER));
        // Validate trước khi ghi: câu hợp lệ đứng trước cũng không được lưu
        assertThat(answerStore).isEmpty();

        assertThatThrownBy(() -> service.saveAnswers(attempt.getAttemptId(), SaveAnswersRequest.builder()
                .answers(List.of(item(mc.getQuestionId(), correctOption(other).getOptionId(), null))).build()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.INVALID_ANSWER));
    }

    @Test
    void result_notAvailableWhileInProgress() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now(), LocalDateTime.now().plusMinutes(30));

        assertThatThrownBy(() -> service.getMyAttemptResult(attempt.getAttemptId()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.ATTEMPT_NOT_SUBMITTED));
    }

    @Test
    void sweeper_closesOverdueAttempts() {
        ExamAttempt attempt = givenAttempt(LocalDateTime.now().minusHours(1), LocalDateTime.now().minusMinutes(30));
        when(attemptRepository.findOverdueAttemptIds(any(), any(), any())).thenReturn(List.of(attempt.getAttemptId()));

        int closed = service.autoCloseOverdueAttempts();

        assertThat(closed).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.EXPIRED);
    }

    // ------------------------------------------------------------------ AI chấm (đề luyện tập)

    /** Đề có thêm 1 câu tự luận 9 điểm trong section Speaking; bài đã nộp, câu tự luận đã làm. */
    private ExamAttempt givenSubmittedSpeakingEssay() {
        ExamSection speaking = ExamSection.builder().sectionId(UUID.randomUUID()).exam(exam)
                .sectionType("SPEAKING").title("Speaking").build();
        Question essay = question(QuestionType.ESSAY, "9");
        ExamQuestion essayQuestion = ExamTestData.examQuestion(exam, essay, 3);
        essayQuestion.setSection(speaking);
        List<ExamQuestion> withEssay = new ArrayList<>(examQuestions);
        withEssay.add(essayQuestion);
        when(examQuestionRepository.findDetailedByExamId(exam.getExamId())).thenReturn(withEssay);

        ExamAttempt attempt = givenAttempt(LocalDateTime.now().minusMinutes(5), LocalDateTime.now().plusMinutes(25));
        attempt.setStatus(AttemptStatus.SUBMITTED);
        answerStore.add(ExamTestData.answer(attempt, essay, null, "My hometown is a small city near the sea."));
        return attempt;
    }

    @Test
    void aiGrade_practiceSpeakingEssayScoredOnTargetScale() {
        exam.setExamScope(ExamScope.PRACTICE);
        ExamAttempt attempt = givenSubmittedSpeakingEssay();
        when(scoringScaleResolver.resolve(studentId, PracticeExamType.SPEAKING)).thenReturn(ScoringScale.IELTS);
        when(scoringScaleResolver.resolve(eq(studentId), any(String.class))).thenReturn(ScoringScale.IELTS);
        when(scoringScaleResolver.resolve(studentId, (String) null)).thenReturn(ScoringScale.IELTS);
        CriterionScoreData criterion = new CriterionScoreData(new BigDecimal("6.0"), "Khá");
        when(aiEvaluationClient.evaluate(eq(PracticeExamType.SPEAKING), any(), any(), eq(ScoringScale.IELTS)))
                .thenReturn(new AiEvaluationResult(new BigDecimal("6.0"), criterion, criterion, criterion, criterion,
                        "Bài nói khá tốt", List.of("Luyện phát âm"), "claude-test", "{}", "B2"));

        AttemptResultResponse result = service.aiGradeAttempt(attempt.getAttemptId());

        ExamAnswer graded = answerStore.get(0);
        // 9 điểm * (6.0 - 0) / (9 - 0) = 6.00
        assertThat(graded.getManualScore()).isEqualByComparingTo("6.00");
        assertThat(graded.getAiEvaluation()).contains("\"skill\":\"SPEAKING\"").contains("\"cefrLevel\":\"B2\"");
        assertThat(attempt.getStatus()).isEqualTo(AttemptStatus.GRADED);
        assertThat(result.getAnswers()).anySatisfy(a -> assertThat(a.getAiEvaluation()).isNotNull());
        // Reading (2 câu bỏ trống) = 0 band, Speaking = 6.0 band → trung bình 3.0, CEFR A2
        assertThat(result.getPracticeLevel().getOverallScore()).isEqualByComparingTo("3.0");
        assertThat(result.getPracticeLevel().getCefrLevel()).isEqualTo("A2");
        assertThat(result.getPracticeLevel().getSkills()).extracting(s -> s.getSkill())
                .containsExactly("SPEAKING", "OTHER");
    }

    @Test
    void aiGrade_onlyForPracticeExams() {
        ExamAttempt attempt = givenSubmittedSpeakingEssay();

        assertThatThrownBy(() -> service.aiGradeAttempt(attempt.getAttemptId()))
                .satisfies(ex -> assertThat(errorOf(ex)).isEqualTo(ErrorCode.INVALID_EXAM_SCOPE));
        verify(aiEvaluationClient, never()).evaluate(any(), any(), any(), any());
    }

    @Test
    void bandToPoints_mapsTargetScaleToQuestionPoints() {
        ScoringScale toeicWriting = new ScoringScale("TOEIC", BigDecimal.ZERO, new BigDecimal("200"), BigDecimal.TEN);
        assertThat(ExamAttemptServiceImpl.bandToPoints(new BigDecimal("150"), toeicWriting, new BigDecimal("4")))
                .isEqualByComparingTo("3.00");
        assertThat(ExamAttemptServiceImpl.bandToPoints(new BigDecimal("9.0"), ScoringScale.IELTS, BigDecimal.ONE))
                .isEqualByComparingTo("1.00");
    }
}
