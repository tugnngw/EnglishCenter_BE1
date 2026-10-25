package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lịch hẹn 1-1. Các mốc thời gian là thời điểm UTC (ISO-8601 có "Z").
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    private UUID bookingId;
    private UUID scheduleId;
    private UUID mentorId;
    private String mentorName;
    private UUID studentId;
    private String studentName;
    private Instant startTime;
    private Instant endTime;
    private BigDecimal price;
    private String meetingUrl;
    private String status;
    private String cancelReason;
    private Instant cancelledAt;
    private UUID rescheduledFromId;
    private Instant createdAt;
    // Mã đơn SePay đang chờ thanh toán (chỉ có khi status = pending). FE gọi GET /payments/orders/{code} để lấy QR
    private String paymentOrderCode;
}
