package com.sba301.englishcenter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.Question;
import com.sba301.englishcenter.entity.QuestionOption;
import com.sba301.englishcenter.util.JsonUtil;
import com.sba301.englishcenter.util.QuestionValidator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Logic chấm điểm thuần (D3-11), không truy cập DB để dễ unit test.
 * <ul>
 *     <li>multiple_choice / true_false: đúng nếu lựa chọn được chọn có {@code is_correct = true};</li>
 *     <li>fill_blank: đúng nếu câu trả lời (đã chuẩn hóa) trùng một đáp án được chấp nhận;</li>
 *     <li>matching: đúng nếu TẤT CẢ các cặp nối đúng (không cho điểm từng phần);</li>
 *     <li>essay: không tự chấm - chờ mentor nhập {@code manual_score}.</li>
 * </ul>
 * Câu đúng được trọn {@code points} của câu, câu sai/bỏ trống được 0.
 */
@Component
public class ExamGrader {

    /** Kết quả tổng hợp sau khi chấm một lượt thi. */
    public record Summary(BigDecimal score, BigDecimal maxScore, BigDecimal percentage, int correct,
                          int incorrect, int unanswered, boolean pendingManual, boolean passed) {
    }

    public boolean isObjective(QuestionType type) {
        return type != QuestionType.ESSAY;
    }

    public boolean isAnswered(Question question, ExamAnswer answer) {
        if (answer == null) {
            return false;
        }
        return switch (question.getQuestionType()) {
            case multiple_choice, true_false -> answer.getSelectedOption() != null;
            case matching -> !parseMatching(answer.getAnswerText()).isEmpty();
            case fill_blank, essay -> answer.getAnswerText() != null && !answer.getAnswerText().isBlank();
        };
    }

    /** Câu essay đã có nội dung nhưng mentor chưa chấm. */
    public boolean isPendingManual(Question question, ExamAnswer answer) {
        return question.getQuestionType() == QuestionType.ESSAY
                && isAnswered(question, answer)
                && answer.getManualScore() == null;
    }

    /** Chấm một câu objective, ghi kết quả vào {@code isCorrect}/{@code earnedScore} của answer. */
    public void gradeAnswer(Question question, ExamAnswer answer) {
        if (!isObjective(question.getQuestionType())) {
            answer.setIsCorrect(false);
            answer.setEarnedScore(answer.getManualScore() != null ? answer.getManualScore() : BigDecimal.ZERO);
            return;
        }
        boolean correct = isAnswered(question, answer) && isCorrect(question, answer);
        answer.setIsCorrect(correct);
        answer.setEarnedScore(correct ? points(question) : BigDecimal.ZERO);
    }

    private boolean isCorrect(Question question, ExamAnswer answer) {
        List<QuestionOption> options = question.getOptions();
        return switch (question.getQuestionType()) {
            case multiple_choice, true_false -> Boolean.TRUE.equals(answer.getSelectedOption().getIsCorrect())
                    && Objects.equals(answer.getSelectedOption().getQuestion().getQuestionId(),
                    question.getQuestionId());
            case fill_blank -> {
                String given = normalizeText(answer.getAnswerText());
                yield options.stream().anyMatch(o -> normalizeText(o.getOptionText()).equals(given));
            }
            case matching -> {
                Map<String, String> expected = new HashMap<>();
                for (QuestionOption option : options) {
                    String[] pair = QuestionValidator.splitPair(option.getOptionText());
                    if (pair != null) {
                        expected.put(normalizeText(pair[0]), normalizeText(pair[1]));
                    }
                }
                yield !expected.isEmpty() && expected.equals(parseMatching(answer.getAnswerText()));
            }
            case essay -> false;
        };
    }

    /**
     * Chấm (lại) toàn bộ lượt thi và cập nhật các trường tổng của attempt.
     *
     * @param answers câu trả lời theo questionId (câu không có trong map = bỏ trống)
     */
    public Summary grade(ExamAttempt attempt, List<ExamQuestion> examQuestions, Map<UUID, ExamAnswer> answers,
                         BigDecimal passingScore) {
        BigDecimal score = BigDecimal.ZERO;
        BigDecimal computedMax = BigDecimal.ZERO;
        int correct = 0;
        int incorrect = 0;
        int unanswered = 0;
        boolean pending = false;

        for (ExamQuestion eq : examQuestions) {
            Question question = eq.getQuestion();
            ExamAnswer answer = answers.get(question.getQuestionId());
            computedMax = computedMax.add(points(question));

            if (answer != null) {
                gradeAnswer(question, answer);
                score = score.add(answer.getEarnedScore());
            }
            if (!isAnswered(question, answer)) {
                unanswered++;
            } else if (isObjective(question.getQuestionType())) {
                if (Boolean.TRUE.equals(answer.getIsCorrect())) {
                    correct++;
                } else {
                    incorrect++;
                }
            } else if (answer.getManualScore() == null) {
                pending = true;
            }
        }

        BigDecimal maxScore = attempt.getMaxScore() != null && attempt.getMaxScore().signum() > 0
                ? attempt.getMaxScore() : computedMax;
        BigDecimal percentage = percentage(score, maxScore);
        BigDecimal threshold = passingScore != null ? passingScore : BigDecimal.ZERO;
        boolean passed = !pending && percentage.compareTo(threshold) >= 0;

        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setCorrectCount(correct);
        attempt.setIncorrectCount(incorrect);
        attempt.setUnansweredCount(unanswered);
        attempt.setPassed(passed);

        return new Summary(score, maxScore, percentage, correct, incorrect, unanswered, pending, passed);
    }

    public static BigDecimal percentage(BigDecimal score, BigDecimal maxScore) {
        if (score == null || maxScore == null || maxScore.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return score.multiply(BigDecimal.valueOf(100)).divide(maxScore, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal points(Question question) {
        return question.getPoints() != null ? question.getPoints() : BigDecimal.ONE;
    }

    /**
     * Chuẩn hóa câu trả lời dạng chữ: Unicode NFC, bỏ khoảng trắng thừa, chữ thường,
     * bỏ dấu chấm câu ở cuối. "  The Cat. " == "the cat".
     */
    public static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[.!?;,]+$", "").trim();
    }

    /** Câu trả lời matching: JSON object {"vế trái": "vế phải"}; sai định dạng = bỏ trống. */
    public static Map<String, String> parseMatching(String json) {
        Map<String, String> result = new HashMap<>();
        JsonNode node = JsonUtil.parse(json);
        if (node == null || !node.isObject()) {
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (field.getValue() != null && field.getValue().isTextual()
                    && !field.getValue().textValue().isBlank()) {
                result.put(normalizeText(field.getKey()), normalizeText(field.getValue().textValue()));
            }
        }
        return result;
    }
}
