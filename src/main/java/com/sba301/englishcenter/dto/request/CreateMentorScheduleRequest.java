package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Mentor mở 1 slot rảnh: start_time, end_time, meeting link (TASK D5-02).
 * Thời gian gửi dạng ISO-8601 UTC, vd "2026-10-25T12:30:00Z" (TASK D5-19).
 * Không nhận price: giá được tính từ hourly_rate của mentor.
 */
@Getter
@Setter
public class CreateMentorScheduleRequest {

    @NotNull(message = "Giờ bắt đầu không được để trống")
    @Future(message = "Giờ bắt đầu phải ở tương lai")
    private Instant startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private Instant endTime;

    @Size(max = 500, message = "Link meeting không được vượt quá 500 ký tự")
    private String meetingUrl;
}
