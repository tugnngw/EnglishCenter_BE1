package com.sba301.englishcenter.dto.consultation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.DayOfWeek;

/**
 * 1 khung giờ rảnh hằng tuần của học viên, lưu trong cột JSONB consultation_requests.available_slots (V26).
 * from / to dạng "HH:mm" theo GIỜ VIỆT NAM (khung lặp mỗi tuần nên không đổi sang UTC).
 * Dùng chung cho request (có validate) và response.
 */
public record AvailableSlotData(
        @NotNull(message = "Thứ trong tuần không được để trống")
        DayOfWeek day,

        @NotNull(message = "Giờ bắt đầu không được để trống")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ bắt đầu phải dạng HH:mm")
        String from,

        @NotNull(message = "Giờ kết thúc không được để trống")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ kết thúc phải dạng HH:mm")
        String to) {
}
