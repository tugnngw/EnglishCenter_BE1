package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

/**
 * Trình độ rút ra từ một lượt làm đề luyện tập, theo thang của chứng chỉ mục tiêu của học viên:
 * điểm tổng (VD IELTS band 5.5) + level CEFR, và điểm từng kỹ năng. Cũng là giá trị lưu vào hồ sơ
 * (student_profiles.current_score / current_level).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PracticeLevelResponse {

    String certificateName;
    BigDecimal overallScore;
    BigDecimal minScore;
    BigDecimal maxScore;
    BigDecimal step;
    /** A1 – C2 */
    String cefrLevel;
    List<SkillLevel> skills;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class SkillLevel {
        /** LISTENING | READING | WRITING | SPEAKING | OTHER */
        String skill;
        BigDecimal score;
        BigDecimal maxScore;
        String cefrLevel;
        /** % điểm đạt của kỹ năng trong đề */
        BigDecimal percentage;
    }
}
