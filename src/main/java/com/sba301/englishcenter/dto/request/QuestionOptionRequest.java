package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Một đáp án của câu hỏi.
 * - multiple_choice / true_false: lựa chọn, đúng một dòng isCorrect = true
 * - fill_blank: đáp án được chấp nhận
 * - matching: cặp "vế trái::vế phải"
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class QuestionOptionRequest {

    @NotBlank(message = "Nội dung đáp án không được để trống")
    String optionText;

    Boolean isCorrect;

    @Min(value = 1, message = "Thứ tự đáp án phải lớn hơn 0")
    Integer optionOrder;

    String explanation;
}
