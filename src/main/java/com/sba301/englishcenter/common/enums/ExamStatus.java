package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái đề thi - ánh xạ trực tiếp với PostgreSQL native enum {@code exam_status_enum}.
 * Hằng số Java viết chữ thường đúng như nhãn trong DB (xem {@code CourseStatus}); các alias VIẾT HOA
 * chỉ để code dễ đọc.
 */
@Getter
@RequiredArgsConstructor
public enum ExamStatus {

    draft("draft"),
    published("published"),
    archived("archived");

    public static final ExamStatus DRAFT = draft;
    public static final ExamStatus PUBLISHED = published;
    public static final ExamStatus ARCHIVED = archived;

    @JsonValue
    private final String value;

    @JsonCreator
    public static ExamStatus fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (ExamStatus item : values()) {
            if (item.name().equals(clean) || item.value.equals(clean)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown ExamStatus: " + value);
    }
}
