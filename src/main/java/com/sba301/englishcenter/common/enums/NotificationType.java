package com.sba301.englishcenter.common.enums;

/**
 * Loại thông báo.
 * Viết thường để khớp đúng giá trị của notification_type_enum trong PostgreSQL (V1__initial_schema.sql).
 */
public enum NotificationType {
    study_reminder,
    homework_deadline,
    payment,
    exam_result,
    mentor_session,
    general
}
