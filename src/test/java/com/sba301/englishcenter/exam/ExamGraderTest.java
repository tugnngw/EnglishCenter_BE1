package com.sba301.englishcenter.exam;

import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.service.ExamGrader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.sba301.englishcenter.exam.ExamTestData.answer;
import static com.sba301.englishcenter.exam.ExamTestData.correctOption;
import static com.sba301.englishcenter.exam.ExamTestData.examQuestions;
import static com.sba301.englishcenter.exam.ExamTestData.question;
import static com.sba301.englishcenter.exam.ExamTestData.wrongOption;
import static org.assertj.core.api.Assertions.assertThat;

class ExamGraderTest {

    private final ExamGrader grader = new ExamGrader();
    private Exam exam;
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        exam = ExamTestData.publishedExam();
        attempt = ExamTestData.inProgressAttempt(exam, ExamTestData.user(UUID.randomUUID()),
                LocalDateTime.now(), LocalDateTime.now().plusMinutes(30));
    }

    @Test
    void multipleChoice_correctAndWrong() {
        Question q = question(QuestionType.MULTIPLE_CHOICE, "2", "*A", "B", "C");

        ExamAnswer right = answer(attempt, q, correctOption(q), null);
        grader.gradeAnswer(q, right);
        assertThat(right.getIsCorrect()).isTrue();
        assertThat(right.getEarnedScore()).isEqualByComparingTo("2");

        ExamAnswer wrong = answer(attempt, q, wrongOption(q), null);
        grader.gradeAnswer(q, wrong);
        assertThat(wrong.getIsCorrect()).isFalse();
        assertThat(wrong.getEarnedScore()).isEqualByComparingTo("0");
    }

    @Test
    void fillBlank_isCaseAndWhitespaceInsensitive() {
        Question q = question(QuestionType.FILL_BLANK, "1", "went", "had gone");

        ExamAnswer a = answer(attempt, q, null, "  Had   GONE. ");
        grader.gradeAnswer(q, a);
        assertThat(a.getIsCorrect()).isTrue();

        ExamAnswer b = answer(attempt, q, null, "go");
        grader.gradeAnswer(q, b);
        assertThat(b.getIsCorrect()).isFalse();
    }

    @Test
    void matching_requiresAllPairs() {
        Question q = question(QuestionType.MATCHING, "3", "dog::chó", "cat::mèo");

        ExamAnswer all = answer(attempt, q, null, "{\"Dog\":\"chó\",\"cat\":\"Mèo\"}");
        grader.gradeAnswer(q, all);
        assertThat(all.getIsCorrect()).isTrue();
        assertThat(all.getEarnedScore()).isEqualByComparingTo("3");

        ExamAnswer partial = answer(attempt, q, null, "{\"dog\":\"mèo\",\"cat\":\"chó\"}");
        grader.gradeAnswer(q, partial);
        assertThat(partial.getIsCorrect()).isFalse();

        ExamAnswer garbage = answer(attempt, q, null, "not json");
        assertThat(grader.isAnswered(q, garbage)).isFalse();
    }

    @Test
    void grade_essayPendingBlocksPassedUntilManualScore() {
        Question mc = question(QuestionType.MULTIPLE_CHOICE, "1", "*A", "B");
        Question essay = question(QuestionType.ESSAY, "4");
        Map<UUID, ExamAnswer> answers = new HashMap<>();
        answers.put(mc.getQuestionId(), answer(attempt, mc, correctOption(mc), null));
        ExamAnswer essayAnswer = answer(attempt, essay, null, "My essay");
        answers.put(essay.getQuestionId(), essayAnswer);

        ExamGrader.Summary pending = grader.grade(attempt, examQuestions(exam, mc, essay), answers,
                new BigDecimal("50"));
        assertThat(pending.pendingManual()).isTrue();
        assertThat(pending.passed()).isFalse();
        assertThat(pending.score()).isEqualByComparingTo("1");
        assertThat(pending.maxScore()).isEqualByComparingTo("5");

        essayAnswer.setManualScore(new BigDecimal("3"));
        ExamGrader.Summary done = grader.grade(attempt, examQuestions(exam, mc, essay), answers,
                new BigDecimal("50"));
        assertThat(done.pendingManual()).isFalse();
        assertThat(done.score()).isEqualByComparingTo("4");
        assertThat(done.percentage()).isEqualByComparingTo("80.00");
        assertThat(done.passed()).isTrue();
        assertThat(attempt.getScore()).isEqualByComparingTo("4");
    }

    @Test
    void grade_countsUnanswered() {
        Question q1 = question(QuestionType.TRUE_FALSE, "1", "*True", "False");
        Question q2 = question(QuestionType.TRUE_FALSE, "1", "True", "*False");
        Question q3 = question(QuestionType.FILL_BLANK, "1", "x");
        Map<UUID, ExamAnswer> answers = new HashMap<>();
        answers.put(q1.getQuestionId(), answer(attempt, q1, correctOption(q1), null));
        answers.put(q2.getQuestionId(), answer(attempt, q2, wrongOption(q2), null));

        ExamGrader.Summary s = grader.grade(attempt, examQuestions(exam, q1, q2, q3), answers, new BigDecimal("50"));

        assertThat(s.correct()).isEqualTo(1);
        assertThat(s.incorrect()).isEqualTo(1);
        assertThat(s.unanswered()).isEqualTo(1);
        assertThat(s.passed()).isFalse();
    }
}
