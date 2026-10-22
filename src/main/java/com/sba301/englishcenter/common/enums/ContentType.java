package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Loại nội dung trong khóa học - ánh xạ trực tiếp với PostgreSQL native enum {@code content_type_enum}.
 * Bảng {@code course_content} gộp chung lesson + vocabulary + homework.
 * Các giá trị trong PostgreSQL enum là chữ thường: 'lesson', 'vocabulary', 'homework'.
 * Do Hibernate @JdbcTypeCode(SqlTypes.NAMED_ENUM) dùng name() của enum Java để bind sang
 * PostgreSQL native enum, các hằng số enum được định nghĩa dạng chữ thường ('lesson', 'vocabulary', 'homework').
 *
 * Các hằng số tĩnh LESSON, VOCABULARY, HOMEWORK được giữ lại làm alias để tương thích ngược.
 */
@Getter
@RequiredArgsConstructor
public enum ContentType {

    lesson("lesson"),
    vocabulary("vocabulary"),
    homework("homework");

    public static final ContentType LESSON = lesson;
    public static final ContentType VOCABULARY = vocabulary;
    public static final ContentType HOMEWORK = homework;

    @JsonValue
    private final String value;

    @JsonCreator
    public static ContentType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (ContentType type : values()) {
            if (type.name().equals(clean) || type.value.equals(clean)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown content type: " + value);
    }
}