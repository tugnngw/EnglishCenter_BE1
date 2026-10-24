package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.QuestionType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Câu hỏi trong đề. Các trường lộ đáp án (isCorrect, explanation, đáp án fill_blank, cặp matching)
 * chỉ có mặt khi người xem có quyền (admin).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamQuestionResponse {

    UUID questionId;
    UUID sectionId;
    Integer questionOrder;

    /** Số thứ tự hiển thị liên tục trong toàn đề (1, 2, 3...). */
    Integer displayNumber;
    Integer timeLimitSeconds;

    QuestionType questionType;
    String questionText;
    BigDecimal points;
    DifficultyLevel difficultyLevel;
    String topic;
    String audioUrl;
    String imageUrl;
    String explanation;
    UUID certificateId;
    UUID certificatePartId;
    Integer usageCount;

    List<QuestionOptionResponse> options;

    /** matching (góc nhìn học viên): các vế trái theo thứ tự và các vế phải đã sắp xếp lại. */
    List<String> matchingLeft;
    List<String> matchingRight;
}
