package com.sba301.englishcenter.common.enums;

/**
 * Trạng thái lịch học cá nhân (TASK-D5-01). Viết thường để khớp study_status_enum trong PostgreSQL (V1).
 * pending: chờ học · done: đã học · skipped: bỏ qua · cancelled: hủy.
 */
public enum StudyStatus {
    pending,
    done,
    skipped,
    cancelled
}
