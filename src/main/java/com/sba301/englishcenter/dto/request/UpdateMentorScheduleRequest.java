package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.ScheduleStatus;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Mentor sửa, đóng hoặc mở lại 1 slot chưa được đặt (TASK D5-03).
 * status chỉ nhận "available" (mở) hoặc "cancelled" (đóng).
 */
@Getter
@Setter
public class UpdateMentorScheduleRequest {

    @NotNull(message = "Giờ bắt đầu không được để trống")
    @Future(message = "Giờ bắt đầu phải ở tương lai")
    private Instant startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private Instant endTime;

    @Size(max = 500, message = "Link meeting không được vượt quá 500 ký tự")
    private String meetingUrl;

    @NotNull(message = "Trạng thái không được để trống")
    private ScheduleStatus status;
}
