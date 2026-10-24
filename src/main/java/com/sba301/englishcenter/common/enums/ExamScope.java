package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Phạm vi đề thi (thi thử theo chứng chỉ / thi của khóa học) - ánh xạ trực tiếp với PostgreSQL native enum {@code exam_scope_enum}.
 * Hằng số Java viết chữ thường đúng như nhãn trong DB (xem {@code CourseStatus}); các alias VIẾT HOA
 * chỉ để code dễ đọc.
 */
@Getter
@RequiredArgsConstructor
public enum ExamScope {

    trial("trial"),
    course("course");

    public static final ExamScope TRIAL = trial;
    public static final ExamScope COURSE = course;

    @JsonValue
    private final String value;

    @JsonCreator
    public static ExamScope fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (ExamScope item : values()) {
            if (item.name().equals(clean) || item.value.equals(clean)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown ExamScope: " + value);
    }
}
