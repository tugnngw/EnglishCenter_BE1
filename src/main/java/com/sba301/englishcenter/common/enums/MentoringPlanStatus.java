package com.sba301.englishcenter.common.enums;

/**
 * Trạng thái gói kèm 1-1 (cột mentoring_plans.status, VARCHAR - V21).
 * pending_payment: mentor đã tạo, chờ học viên thanh toán cả gói ·
 * active: đã thanh toán, các buổi đã xác nhận · completed: đã học xong mọi buổi ·
 * cancelled: mentor hủy / học viên từ chối trước khi thanh toán.
 */
public enum MentoringPlanStatus {
    pending_payment,
    active,
    completed,
    cancelled
}
