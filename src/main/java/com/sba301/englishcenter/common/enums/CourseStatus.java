package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái khóa học - ánh xạ trực tiếp với PostgreSQL native enum {@code course_status_enum}.
 * Các giá trị trong PostgreSQL enum là chữ thường: 'draft', 'active', 'archived'.
 * Do Hibernate @JdbcTypeCode(SqlTypes.NAMED_ENUM) dùng name() của enum Java để bind sang
 * PostgreSQL native enum, các hằng số enum được định nghĩa dạng chữ thường ('draft', 'active', 'archived')
 * để tránh lỗi: org.postgresql.util.PSQLException: ERROR: invalid input value for enum course_status_enum: "ACTIVE".
 *
 * Các hằng số tĩnh DRAFT, ACTIVE, ARCHIVED được giữ lại làm alias để tương thích ngược với mã nguồn hiện tại.
 */
@Getter
@RequiredArgsConstructor
public enum CourseStatus {

    draft("draft"),
    active("active"),
    archived("archived");

    public static final CourseStatus DRAFT = draft;
    public static final CourseStatus ACTIVE = active;
    public static final CourseStatus ARCHIVED = archived;

    @JsonValue
    private final String value;

    @JsonCreator
    public static CourseStatus fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (CourseStatus status : values()) {
            if (status.name().equals(clean) || status.value.equals(clean)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown course status: " + value);
    }
}