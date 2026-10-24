package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

/**
 * Câu trả lời cho một câu hỏi. Gửi cả selectedOptionId và answerText = null để xóa câu trả lời.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AnswerItemRequest {

    @NotNull(message = "questionId không được để trống")
    UUID questionId;

    /** multiple_choice / true_false. */
    UUID selectedOptionId;

    /** fill_blank: đáp án; matching: JSON {"trái":"phải"}; essay: bài viết hoặc URL ghi âm (Speaking). */
    @Size(max = 20000, message = "Câu trả lời không được vượt quá 20000 ký tự")
    String answerText;

    @PositiveOrZero(message = "Thời gian làm câu phải lớn hơn hoặc bằng 0")
    Integer timeSpentSeconds;
}
