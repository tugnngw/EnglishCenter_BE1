package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.dto.response.AnswerResultResponse;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import com.sba301.englishcenter.dto.response.ExamDetailResponse;
import com.sba301.englishcenter.dto.response.ExamQuestionResponse;
import com.sba301.englishcenter.dto.response.ExamSectionResponse;
import com.sba301.englishcenter.dto.response.ExamSummaryResponse;
import com.sba301.englishcenter.dto.response.QuestionOptionResponse;
import com.sba301.englishcenter.dto.response.SavedAnswerResponse;
import com.sba301.englishcenter.dto.response.SectionScoreResponse;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.ExamSection;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.QuestionOption;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.util.QuestionValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chuyển entity của module Exam sang DTO, đồng thời là nơi DUY NHẤT quyết định trường nào bị ẩn
 * (đáp án đúng, giải thích, điểm) theo góc nhìn của người xem.
 */
@Component
@RequiredArgsConstructor
public class ExamMapper {

    /** Mức hiển thị nội dung đề. */
    public enum View {
        /** Admin: toàn bộ câu hỏi kèm đáp án đúng. */
        ADMIN,
        /** Học viên đang làm bài: câu hỏi không kèm đáp án đúng. */
        ATTEMPT,
        /** Xem trước: chỉ cấu trúc section và số câu. */
        OUTLINE
    }

    private final ExamGrader grader;

    // ==================================================================
    // EXAM
    // ==================================================================

    public ExamSummaryResponse toSummary(Exam exam, boolean admin) {
        ExamSummaryResponse.ExamSummaryResponseBuilder builder = ExamSummaryResponse.builder()
                .examId(exam.getExamId())
                .examCode(exam.getExamCode())
                .examTitle(exam.getExamTitle())
                .description(exam.getDescription())
                .examScope(exam.getExamScope())
                .examType(exam.getExamType())
                .status(exam.getStatus())
                .certificateId(exam.getCertificate() != null ? exam.getCertificate().getCertificateId() : null)
                .certificateName(exam.getCertificate() != null ? exam.getCertificate().getCertificateName() : null)
                .courseId(exam.getCourse() != null ? exam.getCourse().getCourseId() : null)
                .courseTitle(exam.getCourse() != null ? exam.getCourse().getTitle() : null)
                .durationMinutes(exam.getDurationMinutes())
                .totalQuestions(exam.getTotalQuestions())
                .totalParts(exam.getTotalParts())
                .passingScore(exam.getPassingScore())
                .maxAttempts(exam.getMaxAttempts())
                .showAnswers(exam.getShowAnswers())
                .showScore(exam.getShowScore())
                .isFree(exam.getIsFree())
                .price(exam.getPrice())
                .difficultyLevel(exam.getDifficultyLevel())
                .recommendedLevel(exam.getRecommendedLevel())
                .availableFrom(exam.getAvailableFrom())
                .availableUntil(exam.getAvailableUntil())
                .publishedDate(exam.getPublishedDate())
                .requiresEnrollment(exam.getExamScope() == ExamScope.COURSE);
        if (admin) {
            builder.totalAttempts(exam.getTotalAttempts())
                    .averageScore(exam.getAverageScore())
                    .createdAt(exam.getCreatedAt())
                    .lastUpdated(exam.getLastUpdated());
        }
        return builder.build();
    }

    public ExamDetailResponse toDetail(Exam exam, List<ExamSection> sections, List<ExamQuestion> examQuestions,
                                       View view, Boolean accessible, String accessMessage) {
        List<ExamQuestion> ordered = orderQuestions(examQuestions);
        Map<UUID, Integer> displayNumbers = new LinkedHashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            displayNumbers.put(ordered.get(i).getQuestion().getQuestionId(), i + 1);
        }

        boolean includeQuestions = view != View.OUTLINE;
        List<ExamSectionResponse> sectionResponses = new ArrayList<>();
        for (ExamSection section : sections) {
            List<ExamQuestion> inSection = ordered.stream()
                    .filter(eq -> eq.getSection() != null
                            && eq.getSection().getSectionId().equals(section.getSectionId()))
                    .toList();
            sectionResponses.add(ExamSectionResponse.builder()
                    .sectionId(section.getSectionId())
                    .sectionType(section.getSectionType())
                    .title(section.getTitle())
                    .instructions(section.getInstructions())
                    .passageText(includeQuestions ? section.getPassageText() : null)
                    .audioUrl(includeQuestions ? section.getAudioUrl() : null)
                    .sectionOrder(section.getSectionOrder())
                    .durationMinutes(section.getDurationMinutes())
                    .certificatePartId(section.getCertificatePart() != null
                            ? section.getCertificatePart().getPartId() : null)
                    .certificatePartName(section.getCertificatePart() != null
                            ? section.getCertificatePart().getPartName() : null)
                    .questionCount(inSection.size())
                    .maxScore(sumPoints(inSection))
                    .questions(includeQuestions
                            ? inSection.stream().map(eq -> toQuestion(eq, displayNumbers, view)).toList()
                            : null)
                    .build());
        }

        List<ExamQuestion> unsectioned = ordered.stream().filter(eq -> eq.getSection() == null).toList();

        return ExamDetailResponse.builder()
                .exam(toSummary(exam, view == View.ADMIN))
                .accessible(accessible)
                .accessMessage(accessMessage)
                .questionsIncluded(includeQuestions)
                .maxScore(sumPoints(ordered))
                .sections(sectionResponses)
                .unsectionedQuestions(includeQuestions
                        ? unsectioned.stream().map(eq -> toQuestion(eq, displayNumbers, view)).toList()
                        : null)
                .build();
    }

    public ExamQuestionResponse toQuestion(ExamQuestion eq, Map<UUID, Integer> displayNumbers, View view) {
        Question q = eq.getQuestion();
        ExamQuestionResponse response = toQuestion(q, view);
        response.setSectionId(eq.getSection() != null ? eq.getSection().getSectionId() : null);
        response.setQuestionOrder(eq.getQuestionOrder());
        response.setDisplayNumber(displayNumbers != null ? displayNumbers.get(q.getQuestionId()) : null);
        response.setTimeLimitSeconds(eq.getTimeLimitSeconds());
        return response;
    }

    /** Câu hỏi ngân hàng (không gắn vị trí trong đề). */
    public ExamQuestionResponse toQuestion(Question q, View view) {
        boolean admin = view == View.ADMIN;
        ExamQuestionResponse.ExamQuestionResponseBuilder builder = ExamQuestionResponse.builder()
                .questionId(q.getQuestionId())
                .questionType(q.getQuestionType())
                .questionText(q.getQuestionText())
                .points(ExamGrader.points(q))
                .difficultyLevel(q.getDifficultyLevel())
                .topic(q.getTopic())
                .audioUrl(q.getAudioUrl())
                .imageUrl(q.getImageUrl());

        if (admin) {
            builder.explanation(q.getExplanation())
                    .certificateId(q.getCertificate() != null ? q.getCertificate().getCertificateId() : null)
                    .certificatePartId(q.getCertificatePart() != null ? q.getCertificatePart().getPartId() : null)
                    .usageCount(q.getUsageCount())
                    .options(q.getOptions().stream().map(o -> toOption(o, true)).toList());
            return builder.build();
        }

        // Góc nhìn học viên: không bao giờ lộ đáp án
        switch (q.getQuestionType()) {
            case multiple_choice, true_false ->
                    builder.options(q.getOptions().stream().map(o -> toOption(o, false)).toList());
            case matching -> {
                List<String> lefts = new ArrayList<>();
                List<String> rights = new ArrayList<>();
                for (QuestionOption option : q.getOptions()) {
                    String[] pair = QuestionValidator.splitPair(option.getOptionText());
                    if (pair != null) {
                        lefts.add(pair[0]);
                        rights.add(pair[1]);
                    }
                }
                // Sắp xếp vế phải theo chữ cái để thứ tự không tiết lộ cặp đúng
                rights.sort(String.CASE_INSENSITIVE_ORDER);
                builder.matchingLeft(lefts).matchingRight(rights);
            }
            case fill_blank, essay -> {
                // fill_blank: options là đáp án được chấp nhận -> ẩn; essay: không có options
            }
        }
        return builder.build();
    }

    public QuestionOptionResponse toOption(QuestionOption option, boolean reveal) {
        return QuestionOptionResponse.builder()
                .optionId(option.getOptionId())
                .optionText(option.getOptionText())
                .optionOrder(option.getOptionOrder())
                .isCorrect(reveal ? option.getIsCorrect() : null)
                .explanation(reveal ? option.getExplanation() : null)
                .build();
    }

    // ==================================================================
    // ATTEMPT
    // ==================================================================

    public AttemptDetailResponse toAttemptDetail(ExamAttempt attempt, ExamDetailResponse exam,
                                                 List<ExamAnswer> answers, LocalDateTime now) {
        Exam e = attempt.getExam();
        long remaining = attempt.getExpiresAt() == null ? 0
                : Math.max(0, Duration.between(now, attempt.getExpiresAt()).getSeconds());
        return AttemptDetailResponse.builder()
                .attemptId(attempt.getAttemptId())
                .examId(e.getExamId())
                .examTitle(e.getExamTitle())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus())
                .startTime(attempt.getStartTime())
                .expiresAt(attempt.getExpiresAt())
                .remainingSeconds(attempt.getStatus() == AttemptStatus.IN_PROGRESS
                        ? remaining : 0L)
                .endTime(attempt.getEndTime())
                .durationMinutes(e.getDurationMinutes())
                .autoSubmitted(attempt.getAutoSubmitted())
                .exam(exam)
                .answers(answers.stream().map(this::toSavedAnswer).toList())
                .build();
    }

    public SavedAnswerResponse toSavedAnswer(ExamAnswer answer) {
        return SavedAnswerResponse.builder()
                .questionId(answer.getQuestion().getQuestionId())
                .selectedOptionId(answer.getSelectedOption() != null ? answer.getSelectedOption().getOptionId() : null)
                .answerText(answer.getAnswerText())
                .timeSpentSeconds(answer.getTimeSpentSeconds())
                .answeredDate(answer.getAnsweredDate())
                .build();
    }

    /**
     * @param scoreVisible false: ẩn điểm (đề có show_score = false và người xem là học viên)
     * @param pending      còn câu Writing/Speaking chờ chấm
     */
    public AttemptSummaryResponse toAttemptSummary(ExamAttempt attempt, boolean scoreVisible, boolean pending,
                                                   boolean includeStudent) {
        Exam e = attempt.getExam();
        boolean finished = attempt.getStatus() != AttemptStatus.IN_PROGRESS;
        boolean showScore = scoreVisible && finished;
        AttemptSummaryResponse.AttemptSummaryResponseBuilder builder = AttemptSummaryResponse.builder()
                .attemptId(attempt.getAttemptId())
                .examId(e.getExamId())
                .examTitle(e.getExamTitle())
                .examCode(e.getExamCode())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus())
                .startTime(attempt.getStartTime())
                .expiresAt(attempt.getExpiresAt())
                .endTime(attempt.getEndTime())
                .timeSpentSeconds(attempt.getTimeSpentSeconds())
                .score(showScore ? attempt.getScore() : null)
                .maxScore(showScore ? attempt.getMaxScore() : null)
                .percentage(showScore ? ExamGrader.percentage(attempt.getScore(), attempt.getMaxScore()) : null)
                .passed(showScore && !pending ? attempt.getPassed() : null)
                .pendingManualGrading(finished ? pending : null)
                .autoSubmitted(attempt.getAutoSubmitted());
        if (includeStudent && attempt.getStudent() != null) {
            User student = attempt.getStudent();
            builder.studentId(student.getUserId())
                    .studentName(student.getFullName())
                    .studentEmail(student.getEmail());
        }
        return builder.build();
    }

    /**
     * Kết quả chi tiết.
     *
     * @param staff true nếu người xem là admin/mentor (luôn thấy điểm, đáp án, bài làm)
     */
    public AttemptResultResponse toResult(ExamAttempt attempt, List<ExamQuestion> examQuestions,
                                          Map<UUID, ExamAnswer> answers, boolean staff) {
        Exam exam = attempt.getExam();
        boolean scoreVisible = staff || !Boolean.FALSE.equals(exam.getShowScore());
        boolean answersVisible = staff || !Boolean.FALSE.equals(exam.getShowAnswers());

        List<ExamQuestion> ordered = orderQuestions(examQuestions);
        boolean pendingAny = false;
        List<AnswerResultResponse> answerResults = new ArrayList<>();
        Map<UUID, SectionAccumulator> sectionScores = new LinkedHashMap<>();

        for (int i = 0; i < ordered.size(); i++) {
            ExamQuestion eq = ordered.get(i);
            Question q = eq.getQuestion();
            ExamAnswer answer = answers.get(q.getQuestionId());
            boolean answered = grader.isAnswered(q, answer);
            boolean pending = grader.isPendingManual(q, answer);
            boolean objective = grader.isObjective(q.getQuestionType());
            pendingAny |= pending;
            BigDecimal earned = answer != null ? answer.getEarnedScore() : BigDecimal.ZERO;
            boolean correct = objective && answer != null && Boolean.TRUE.equals(answer.getIsCorrect());

            AnswerResultResponse.AnswerResultResponseBuilder builder = AnswerResultResponse.builder()
                    .questionId(q.getQuestionId())
                    .sectionId(eq.getSection() != null ? eq.getSection().getSectionId() : null)
                    .displayNumber(i + 1)
                    .questionType(q.getQuestionType())
                    .questionText(q.getQuestionText())
                    .points(ExamGrader.points(q))
                    .answered(answered)
                    .selectedOptionId(answer != null && answer.getSelectedOption() != null
                            ? answer.getSelectedOption().getOptionId() : null)
                    .selectedOptionText(answer != null && answer.getSelectedOption() != null
                            ? answer.getSelectedOption().getOptionText() : null)
                    .answerText(answer != null ? answer.getAnswerText() : null)
                    .correct(objective && answersVisible ? correct : null)
                    .earnedScore(scoreVisible && !pending ? earned : null)
                    .pendingManualGrading(q.getQuestionType() == QuestionType.ESSAY ? pending : null)
                    .mentorFeedback(answer != null ? answer.getMentorFeedback() : null)
                    .gradedDate(answer != null ? answer.getGradedDate() : null);

            if (answersVisible) {
                builder.explanation(q.getExplanation());
                if (q.getQuestionType() == QuestionType.MULTIPLE_CHOICE || q.getQuestionType() == QuestionType.TRUE_FALSE) {
                    builder.correctOptionIds(q.getOptions().stream()
                            .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                            .map(QuestionOption::getOptionId).toList());
                    builder.correctAnswers(q.getOptions().stream()
                            .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                            .map(QuestionOption::getOptionText).toList());
                } else if (objective) {
                    builder.correctAnswers(q.getOptions().stream().map(QuestionOption::getOptionText).toList());
                }
            }
            answerResults.add(builder.build());

            if (eq.getSection() != null) {
                sectionScores.computeIfAbsent(eq.getSection().getSectionId(), id -> new SectionAccumulator(eq.getSection()))
                        .add(ExamGrader.points(q), earned, correct, pending);
            }
        }

        List<SectionScoreResponse> sections = scoreVisible
                ? sectionScores.values().stream().map(SectionAccumulator::toResponse).toList()
                : null;

        return AttemptResultResponse.builder()
                .attempt(toAttemptSummary(attempt, scoreVisible, pendingAny, staff))
                .correctCount(answersVisible ? attempt.getCorrectCount() : null)
                .incorrectCount(answersVisible ? attempt.getIncorrectCount() : null)
                .unansweredCount(attempt.getUnansweredCount())
                .scoreVisible(scoreVisible)
                .answersVisible(answersVisible)
                .sectionScores(sections)
                .answers(answerResults)
                .build();
    }

    // ==================================================================
    // HELPERS
    // ==================================================================

    /** Thứ tự hiển thị: theo thứ tự section (câu không có section đứng cuối), rồi theo questionOrder. */
    public static List<ExamQuestion> orderQuestions(List<ExamQuestion> examQuestions) {
        return examQuestions.stream()
                .sorted(Comparator
                        .comparing((ExamQuestion eq) -> eq.getSection() != null
                                ? eq.getSection().getSectionOrder() : Integer.MAX_VALUE)
                        .thenComparing(eq -> eq.getSection() != null && eq.getSection().getCreatedAt() != null
                                ? eq.getSection().getCreatedAt() : LocalDateTime.MAX)
                        .thenComparing(ExamQuestion::getQuestionOrder))
                .toList();
    }

    private static BigDecimal sumPoints(List<ExamQuestion> examQuestions) {
        return examQuestions.stream()
                .map(eq -> ExamGrader.points(eq.getQuestion()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static final class SectionAccumulator {
        private final ExamSection section;
        private BigDecimal max = BigDecimal.ZERO;
        private BigDecimal earned = BigDecimal.ZERO;
        private int count;
        private int correct;
        private boolean pending;

        private SectionAccumulator(ExamSection section) {
            this.section = section;
        }

        private void add(BigDecimal points, BigDecimal earnedScore, boolean isCorrect, boolean isPending) {
            max = max.add(points);
            earned = earned.add(earnedScore != null ? earnedScore : BigDecimal.ZERO);
            count++;
            correct += isCorrect ? 1 : 0;
            pending |= isPending;
        }

        private SectionScoreResponse toResponse() {
            return SectionScoreResponse.builder()
                    .sectionId(section.getSectionId())
                    .certificatePartId(section.getCertificatePart() != null
                            ? section.getCertificatePart().getPartId() : null)
                    .sectionType(section.getSectionType())
                    .title(section.getTitle())
                    .questionCount(count)
                    .correctCount(correct)
                    .earnedScore(earned)
                    .maxScore(max)
                    .percentage(ExamGrader.percentage(earned, max))
                    .pendingManualGrading(pending)
                    .build();
        }
    }
}
