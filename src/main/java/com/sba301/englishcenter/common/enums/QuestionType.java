package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Loại câu hỏi - ánh xạ trực tiếp với PostgreSQL native enum {@code question_type_enum}.
 * Hằng số Java viết chữ thường đúng như nhãn trong DB (xem {@code CourseStatus}); các alias VIẾT HOA
 * chỉ để code dễ đọc.
 */
@Getter
@RequiredArgsConstructor
public enum QuestionType {

    multiple_choice("multiple_choice"),
    fill_blank("fill_blank"),
    essay("essay"),
    matching("matching"),
    true_false("true_false");

    public static final QuestionType MULTIPLE_CHOICE = multiple_choice;
    public static final QuestionType FILL_BLANK = fill_blank;
    public static final QuestionType ESSAY = essay;
    public static final QuestionType MATCHING = matching;
    public static final QuestionType TRUE_FALSE = true_false;

    @JsonValue
    private final String value;

    @JsonCreator
    public static QuestionType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (QuestionType item : values()) {
            if (item.name().equals(clean) || item.value.equals(clean)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown QuestionType: " + value);
    }
}
