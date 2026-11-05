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
    /** Dev 5: giải thích vì sao ra điểm tổng này (cách tính, kỹ năng kéo điểm xuống, kỹ năng bỏ trống) */
    List<String> summary;

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
        /** Tên phần thi của chứng chỉ (VD "Grammar & Vocabulary" cho Core của Aptis); null = tên kỹ năng */
        String name;
        /** true = điểm riêng, không tính vào điểm tổng (VD Aptis Core) */
        Boolean excludeFromTotal;
        /** Dev 5: số câu của kỹ năng / số câu có trả lời / số câu đúng (câu trắc nghiệm, điền từ, nối) */
        Integer questionCount;
        Integer answeredCount;
        Integer correctCount;
        /** Dev 5: nhận xét kỹ năng (đúng bao nhiêu câu, yếu ở part nào / tiêu chí nào) */
        String comment;
        /** Dev 5: kết quả từng part (section) của kỹ năng */
        List<PartResult> parts;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PartResult {
        /** Tên section (VD "Listening Part 2") */
        String name;
        int questionCount;
        int answeredCount;
        /** Số câu đúng (chỉ câu chấm tự động); null nếu part toàn câu tự luận */
        Integer correctCount;
        BigDecimal percentage;
    }
}
