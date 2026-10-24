package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Loại đề thi - ánh xạ với PostgreSQL native enum {@code exam_type_enum}
 * ('quiz', 'midterm', 'final', 'placement_test').
 * <p>
 * Nhãn {@code final} của DB trùng từ khóa Java nên không thể đặt làm tên hằng số; hằng số Java là
 * {@code final_exam} còn giá trị lưu DB / JSON vẫn là {@code "final"}. Việc chuyển đổi do
 * {@link com.sba301.englishcenter.common.converters.ExamTypeConverter} đảm nhiệm.
 */
@Getter
@RequiredArgsConstructor
public enum ExamType {

    quiz("quiz"),
    midterm("midterm"),
    final_exam("final"),
    placement_test("placement_test");

    public static final ExamType QUIZ = quiz;
    public static final ExamType MIDTERM = midterm;
    public static final ExamType FINAL = final_exam;
    public static final ExamType PLACEMENT_TEST = placement_test;

    @JsonValue
    private final String value;

    @JsonCreator
    public static ExamType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (ExamType item : values()) {
            if (item.name().equals(clean) || item.value.equals(clean)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown ExamType: " + value);
    }
}
