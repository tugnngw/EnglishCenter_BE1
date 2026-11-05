package com.sba301.englishcenter.service;

import java.util.UUID;

/**
 * Cầu nối Dev 4 (PaymentService) -> Dev 5 (gói kèm 1-1) cho đơn MENTOR_PLAN.
 * Tách riêng khỏi MentoringPlanService để PaymentServiceImpl không phụ thuộc vòng.
 * Được gọi bên trong transaction webhook của PaymentServiceImpl.
 */
public interface MentoringPlanPaymentService {

    /**
     * Webhook báo đã thanh toán: gói pending_payment -> active, mọi buổi pending -> confirmed, gửi thông báo.
     *
     * @return scheduleId của buổi đầu tiên; null nếu gói không còn chờ thanh toán (đơn chuyển REVIEW_REQUIRED)
     */
    UUID confirmPaidPlan(UUID planId);
}
