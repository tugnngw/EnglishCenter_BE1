package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Độ khó - ánh xạ trực tiếp với PostgreSQL native enum {@code difficulty_level_enum}.
 * Hằng số Java viết chữ thường đúng như nhãn trong DB (xem {@code CourseStatus}); các alias VIẾT HOA
 * chỉ để code dễ đọc.
 */
@Getter
@RequiredArgsConstructor
public enum DifficultyLevel {

    easy("easy"),
    medium("medium"),
    hard("hard"),
    real_test_level("real_test_level");

    public static final DifficultyLevel EASY = easy;
    public static final DifficultyLevel MEDIUM = medium;
    public static final DifficultyLevel HARD = hard;
    public static final DifficultyLevel REAL_TEST_LEVEL = real_test_level;

    @JsonValue
    private final String value;

    @JsonCreator
    public static DifficultyLevel fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (DifficultyLevel item : values()) {
            if (item.name().equals(clean) || item.value.equals(clean)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown DifficultyLevel: " + value);
    }
}
