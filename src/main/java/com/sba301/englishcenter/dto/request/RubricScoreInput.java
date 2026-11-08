package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Điểm + nhận xét 1-1 của MỘT tiêu chí rubric mà mentor chấm.
 * <p>
 * Dùng cho giao diện chấm song song: mỗi tiêu chí rubric có 1 ô điểm và
 * 1 khung nhận xét riêng.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RubricScoreInput {

    /** Tên tiêu chí (khớp {@code rubric[].criterion} của bài tập). */
    String criterion;

    /** Điểm mentor chấm cho tiêu chí này. */
    @PositiveOrZero(message = "Điểm tiêu chí phải lớn hơn hoặc bằng 0")
    BigDecimal score;

    /** Nhận xét 1-1 cho tiêu chí này. */
    String comment;
}
