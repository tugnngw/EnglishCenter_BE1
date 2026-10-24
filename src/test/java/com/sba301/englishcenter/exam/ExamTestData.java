package com.sba301.englishcenter.exam;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.QuestionOption;
import com.sba301.englishcenter.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Dựng nhanh dữ liệu exam trong bộ nhớ cho unit test. */
public final class ExamTestData {

    private ExamTestData() {
    }

    public static User user(UUID id) {
        return User.builder().userId(id).email(id + "@test.local").fullName("Test " + id).build();
    }

    public static Exam publishedExam() {
        return Exam.builder()
                .examId(UUID.randomUUID())
                .examScope(ExamScope.TRIAL)
                .examTitle("Sample exam")
                .durationMinutes(30)
                .maxAttempts(2)
                .passingScore(new BigDecimal("50"))
                .status(ExamStatus.PUBLISHED)
                .build();
    }

    /** Câu hỏi với options dạng "text" hoặc "*text" (dấu * = đáp án đúng). */
    public static Question question(QuestionType type, String points, String... options) {
        Question q = Question.builder()
                .questionId(UUID.randomUUID())
                .questionType(type)
                .questionText(type + " question")
                .points(new BigDecimal(points))
                .options(new ArrayList<>())
                .build();
        int order = 1;
        for (String option : options) {
            boolean correct = option.startsWith("*");
            q.getOptions().add(QuestionOption.builder()
                    .optionId(UUID.randomUUID())
                    .question(q)
                    .optionText(correct ? option.substring(1) : option)
                    .isCorrect(correct || type == QuestionType.FILL_BLANK || type == QuestionType.MATCHING)
                    .optionOrder(order++)
                    .build());
        }
        return q;
    }

    public static ExamQuestion examQuestion(Exam exam, Question q, int order) {
        return ExamQuestion.builder().id(UUID.randomUUID()).exam(exam).question(q).questionOrder(order).build();
    }

    public static ExamAttempt inProgressAttempt(Exam exam, User student, LocalDateTime start, LocalDateTime expires) {
        return ExamAttempt.builder()
                .attemptId(UUID.randomUUID())
                .exam(exam)
                .student(student)
                .attemptNumber(1)
                .startTime(start)
                .expiresAt(expires)
                .status(AttemptStatus.IN_PROGRESS)
                .build();
    }

    public static ExamAnswer answer(ExamAttempt attempt, Question q, QuestionOption selected, String text) {
        return ExamAnswer.builder()
                .detailId(UUID.randomUUID())
                .attempt(attempt)
                .question(q)
                .selectedOption(selected)
                .answerText(text)
                .build();
    }

    public static QuestionOption correctOption(Question q) {
        return q.getOptions().stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).findFirst().orElseThrow();
    }

    public static QuestionOption wrongOption(Question q) {
        return q.getOptions().stream().filter(o -> !Boolean.TRUE.equals(o.getIsCorrect())).findFirst().orElseThrow();
    }

    public static List<ExamQuestion> examQuestions(Exam exam, Question... questions) {
        List<ExamQuestion> list = new ArrayList<>();
        for (int i = 0; i < questions.length; i++) {
            list.add(examQuestion(exam, questions[i], i + 1));
        }
        return list;
    }
}
