package com.sba301.englishcenter.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mentor chấm bài tập.
 * <p>
 * Giao diện chấm song song (split screen): mentor điền điểm + nhận xét cho TỪNG
 * tiêu chí rubric → {@code rubricScores}. Tổng điểm cuối cùng là {@code score}
 * (mentor chốt) — nếu không chốt thì backend tự cộng từ các tiêu chí.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GradeHomeworkSubmissionRequest {

    /**
     * Điểm tổng cuối cùng của bài.
     * <p>
     * Bắt buộc (mentor chốt điểm chính thức). Nếu {@code rubricScores} được gửi
     * kèm mà điểm này khác tổng rubric → backend ưu tiên điểm này.
     */
    @NotNull(message = "Điểm không được để trống")
    @PositiveOrZero(message = "Điểm phải lớn hơn hoặc bằng 0")
    BigDecimal score;

    /** Nhận xét tổng quát cho học viên. */
    String feedback;

    /** Điểm + nhận xét 1-1 cho từng tiêu chí rubric (nếu bài tập có rubric). */
    @Valid
    List<RubricScoreInput> rubricScores;
}
