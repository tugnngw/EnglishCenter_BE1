package com.sba301.englishcenter.common.enums;

/**
 * Trạng thái slot lịch của mentor.
 * Viết thường để khớp đúng giá trị của schedule_status_enum trong PostgreSQL (V1__initial_schema.sql).
 */
public enum ScheduleStatus {
    available,
    booked,
    completed,
    cancelled
}
