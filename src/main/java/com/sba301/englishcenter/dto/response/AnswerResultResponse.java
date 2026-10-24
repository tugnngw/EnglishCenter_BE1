package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sba301.englishcenter.common.enums.QuestionType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Kết quả từng câu hỏi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnswerResultResponse {

    UUID questionId;
    UUID sectionId;
    Integer displayNumber;
    QuestionType questionType;
    String questionText;
    BigDecimal points;

    Boolean answered;
    UUID selectedOptionId;
    String selectedOptionText;
    String answerText;

    /** null với câu essay (chấm tay). */
    Boolean correct;
    BigDecimal earnedScore;
    Boolean pendingManualGrading;

    /** Chỉ có khi đề cho xem đáp án. */
    List<UUID> correctOptionIds;
    List<String> correctAnswers;
    String explanation;

    /** Nhận xét của mentor (Writing/Speaking). */
    String mentorFeedback;
    LocalDateTime gradedDate;
}
