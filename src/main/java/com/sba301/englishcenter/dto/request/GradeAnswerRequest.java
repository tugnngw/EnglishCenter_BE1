package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Mentor/Admin chấm tay một câu Writing/Speaking.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GradeAnswerRequest {

    /** Từ 0 đến điểm tối đa của câu hỏi. */
    @NotNull(message = "Điểm không được để trống")
    @DecimalMin(value = "0.0", message = "Điểm phải lớn hơn hoặc bằng 0")
    BigDecimal score;

    @Size(max = 5000, message = "Nhận xét không được vượt quá 5000 ký tự")
    String feedback;
}
