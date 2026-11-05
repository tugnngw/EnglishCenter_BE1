package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 1 buổi trong gói kèm 1-1. Thời gian gửi dạng ISO-8601 UTC, vd "2026-11-10T12:00:00Z" (TASK D5-19).
 */
@Getter
@Setter
public class PlanSessionRequest {

    @NotNull(message = "Giờ bắt đầu không được để trống")
    private Instant startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private Instant endTime;
}
