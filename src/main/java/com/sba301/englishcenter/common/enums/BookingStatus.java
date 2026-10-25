package com.sba301.englishcenter.common.enums;

/**
 * Trạng thái lượt đặt lịch 1-1 (TASK D5-11).
 * Viết thường để khớp đúng giá trị của booking_status_enum trong PostgreSQL (V6__d5_booking_notification.sql).
 */
public enum BookingStatus {
    pending,
    confirmed,
    completed,
    cancelled,
    no_show
}
