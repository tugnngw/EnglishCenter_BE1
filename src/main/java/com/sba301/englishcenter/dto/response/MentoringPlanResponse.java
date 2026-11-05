package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gói kèm 1-1. Các mốc thời gian là thời điểm UTC (ISO-8601 có "Z").
 * sessions: các buổi của gói (booking), theo giờ bắt đầu.
 * paymentOrderCode: mã đơn SePay đang chờ (chỉ khi gói đang chờ thanh toán và đã tạo đơn).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentoringPlanResponse {

    private UUID planId;
    private UUID mentorId;
    private String mentorName;
    private UUID studentId;
    private String studentName;
    private UUID learningPathId;
    private String learningPathName;
    private UUID consultationId;
    private String title;
    private String note;
    private int totalSessions;
    private int completedSessions;
    private BigDecimal totalPrice;
    private String status;
    private String cancelReason;
    private Instant paidAt;
    private Instant createdAt;
    private String paymentOrderCode;
    private List<BookingResponse> sessions;
}
