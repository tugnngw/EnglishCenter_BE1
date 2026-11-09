package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 1 buổi trong gói kèm 1-1, kèm nội dung buổi (topic, V26). Thời gian gửi dạng ISO-8601 UTC, vd "2026-11-10T12:00:00Z" (TASK D5-19).
 */
@Getter
@Setter
public class PlanSessionRequest {

    @NotNull(message = "Giờ bắt đầu không được để trống")
    private Instant startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private Instant endTime;

    // Nội dung buổi học (V26), không bắt buộc
    @Size(max = 255, message = "Nội dung buổi không được vượt quá 255 ký tự")
    private String topic;
}
