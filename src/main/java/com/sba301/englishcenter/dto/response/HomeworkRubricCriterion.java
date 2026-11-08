package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

/**
 * Một tiêu chí chấm điểm (rubric) do Admin cấu hình cho bài tập.
 * <p>
 * Nguồn: {@code course_content.content_data.rubric} — mảng các object
 * {@code { criterion, description, maxScore? }}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HomeworkRubricCriterion {

    /** Tên tiêu chí (VD: "Task Achievement"). */
    String criterion;

    /** Mô tả chi tiết tiêu chí (có thể trống). */
    String description;

    /** Điểm tối đa của tiêu chí (null = chưa cấu hình → mentor tự chấm tổng). */
    BigDecimal maxScore;
}
