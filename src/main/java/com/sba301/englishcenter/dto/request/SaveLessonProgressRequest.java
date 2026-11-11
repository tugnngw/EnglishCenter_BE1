package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Client gửi tiến độ xem video lên server.
 * <p>
 * BE dùng {@code currentTime} + {@code durationSeconds} để chống tua và đánh dấu hoàn thành.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SaveLessonProgressRequest {

    /** Giây hiện tại của video. */
    @NotNull(message = "Thiếu thời điểm hiện tại của video")
    @Min(value = 0, message = "Thời điểm hiện tại không hợp lệ")
    Integer currentTime;

    /** Tổng thời lượng video (giây). */
    @Min(value = 0, message = "Thời lượng video không hợp lệ")
    Integer durationSeconds;
}
