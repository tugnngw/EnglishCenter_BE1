package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Mentor dời 1 buổi kèm 1-1 sang giờ khác (V26).
 * Chỉ gửi giờ bắt đầu mới (ISO-8601 UTC, TASK D5-19): giờ kết thúc = giờ bắt đầu mới + thời lượng cũ,
 * để học phí buổi (đã / sẽ thanh toán) không đổi.
 */
@Getter
@Setter
public class RescheduleBookingRequest {

    @NotNull(message = "Giờ bắt đầu mới không được để trống")
    private Instant newStartTime;

    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String reason;
}
