package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.ExamType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Admin cập nhật đề thi. Trường null = giữ nguyên.
 * certificateId / courseId chỉ đổi được khi đề còn draft và chưa có lượt thi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateExamRequest {

    UUID certificateId;

    UUID courseId;

    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    String examTitle;

    @Size(max = 50, message = "Mã đề thi không được vượt quá 50 ký tự")
    String examCode;

    /** quiz / midterm / final / placement_test */
    ExamType examType;

    String description;

    @Min(value = 1, message = "Thời lượng làm bài tối thiểu 1 phút")
    @Max(value = 600, message = "Thời lượng làm bài tối đa 600 phút")
    Integer durationMinutes;

    /** Phần trăm điểm tối thiểu để đạt (0-100). */
    @DecimalMin(value = "0.0", message = "Điểm đạt phải từ 0 đến 100")
    @DecimalMax(value = "100.0", message = "Điểm đạt phải từ 0 đến 100")
    BigDecimal passingScore;

    /** Số lượt thi tối đa mỗi học viên; 0 = không giới hạn. */
    @PositiveOrZero(message = "Số lượt thi tối đa phải lớn hơn hoặc bằng 0")
    Integer maxAttempts;

    /** Cho học viên xem đáp án đúng và giải thích sau khi nộp. */
    Boolean showAnswers;

    /** Cho học viên xem điểm sau khi nộp. */
    Boolean showScore;

    /** Chỉ áp dụng cho đề trial: false nghĩa là phải thanh toán (Dev 4) mới được làm. */
    Boolean isFree;

    @DecimalMin(value = "0.0", message = "Giá đề thi phải lớn hơn hoặc bằng 0")
    BigDecimal price;

    DifficultyLevel difficultyLevel;

    @Size(max = 100, message = "Level không được vượt quá 100 ký tự")
    String recommendedLevel;

    LocalDateTime availableFrom;

    LocalDateTime availableUntil;
}
