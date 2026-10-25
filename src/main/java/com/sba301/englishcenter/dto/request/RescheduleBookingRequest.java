package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Đổi lịch hẹn sang 1 slot khác của cùng mentor (TASK D5-10).
 */
@Getter
@Setter
public class RescheduleBookingRequest {

    @NotNull(message = "newScheduleId không được để trống")
    private UUID newScheduleId;
}
