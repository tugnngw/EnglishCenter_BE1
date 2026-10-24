package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái lượt thi - ánh xạ trực tiếp với PostgreSQL native enum {@code attempt_status_enum}.
 * Hằng số Java viết chữ thường đúng như nhãn trong DB (xem {@code CourseStatus}); các alias VIẾT HOA
 * chỉ để code dễ đọc.
 */
@Getter
@RequiredArgsConstructor
public enum AttemptStatus {

    in_progress("in_progress"),
    submitted("submitted"),
    graded("graded"),
    expired("expired");

    public static final AttemptStatus IN_PROGRESS = in_progress;
    public static final AttemptStatus SUBMITTED = submitted;
    public static final AttemptStatus GRADED = graded;
    public static final AttemptStatus EXPIRED = expired;

    @JsonValue
    private final String value;

    @JsonCreator
    public static AttemptStatus fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (AttemptStatus item : values()) {
            if (item.name().equals(clean) || item.value.equals(clean)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown AttemptStatus: " + value);
    }
}
