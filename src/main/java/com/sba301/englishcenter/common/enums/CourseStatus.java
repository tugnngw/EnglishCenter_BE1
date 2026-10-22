package com.sba301.englishcenter.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái khóa học - ánh xạ với PostgreSQL enum {@code course_status_enum}.
 * Cấu hình trong application.properties đã bật Hibernate hỗ trợ PostgreSQL enum,
 * nên enum được lưu trực tiếp dưới dạng VARCHAR theo tên hằng số.
 */
@Getter
@RequiredArgsConstructor
public enum CourseStatus {

    DRAFT("draft"),
    ACTIVE("active"),
    ARCHIVED("archived");

    private final String value;
}