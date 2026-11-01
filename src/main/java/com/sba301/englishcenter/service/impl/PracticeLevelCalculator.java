package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.dto.response.PracticeLevelResponse;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.ExamSection;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.service.ExamGrader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quy kết quả một lượt làm đề luyện tập ra trình độ theo chứng chỉ mục tiêu của học viên.
 * <ol>
 *     <li>Gộp các section cùng kỹ năng (VD Listening Part 1 + Part 2) → % điểm của kỹ năng;</li>
 *     <li>% → điểm theo thang của phần thi đó (Writing / Speaking: % = band AI chấm quy ra, nên khớp band AI);</li>
 *     <li>Điểm tổng = trung bình tỷ lệ các kỹ năng quy ra thang chung, làm tròn theo bước (IELTS: 0.5);</li>
 *     <li>CEFR theo bảng quy đổi của chứng chỉ (IELTS / TOEIC / VSTEP / TOEFL), chứng chỉ khác theo %.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class PracticeLevelCalculator {

    private static final List<String> SKILL_ORDER = List.of("LISTENING", "READING", "WRITING", "SPEAKING", "OTHER");

    private final ScoringScaleResolver scoringScaleResolver;
    private final ExamGrader grader;

    /** @return null nếu còn câu tự luận chưa chấm hoặc đề không có câu nào */
    public PracticeLevelResponse calculate(UUID studentId, List<ExamQuestion> examQuestions,
                                           Map<UUID, ExamAnswer> answers) {
        Map<String, BigDecimal[]> bySkill = new LinkedHashMap<>();
        SKILL_ORDER.forEach(s -> bySkill.put(s, null));
        for (ExamQuestion eq : examQuestions) {
            ExamAnswer answer = answers.get(eq.getQuestion().getQuestionId());
            if (grader.isPendingManual(eq.getQuestion(), answer)) {
                return null;
            }
            // [điểm đạt, điểm tối đa] của kỹ năng; map đã có sẵn khóa theo thứ tự L - R - W - S
            String skill = skillOf(eq.getSection());
            BigDecimal[] acc = bySkill.get(skill);
            if (acc == null) {
                acc = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
                bySkill.put(skill, acc);
            }
            acc[0] = acc[0].add(answer != null && answer.getEarnedScore() != null ? answer.getEarnedScore() : BigDecimal.ZERO);
            acc[1] = acc[1].add(ExamGrader.points(eq.getQuestion()));
        }

        ScoringScale overallScale = scoringScaleResolver.resolve(studentId, (String) null);
        List<PracticeLevelResponse.SkillLevel> skills = new ArrayList<>();
        BigDecimal ratioSum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> e : bySkill.entrySet()) {
            if (e.getValue() == null || e.getValue()[1].signum() <= 0) {
                continue;
            }
            BigDecimal ratio = e.getValue()[0].divide(e.getValue()[1], 6, RoundingMode.HALF_UP);
            ScoringScale scale = "OTHER".equals(e.getKey()) ? overallScale
                    : scoringScaleResolver.resolve(studentId, e.getKey());
            BigDecimal score = toScale(ratio, scale);
            ratioSum = ratioSum.add(ratioOf(score, scale));
            skills.add(PracticeLevelResponse.SkillLevel.builder()
                    .skill(e.getKey())
                    .score(score)
                    .maxScore(scale.maxScore())
                    .cefrLevel(cefr(scale, score))
                    .percentage(ExamGrader.percentage(e.getValue()[0], e.getValue()[1]))
                    .build());
        }
        if (skills.isEmpty()) {
            return null;
        }

        BigDecimal overall = toScale(ratioSum.divide(BigDecimal.valueOf(skills.size()), 6, RoundingMode.HALF_UP),
                overallScale);
        return PracticeLevelResponse.builder()
                .certificateName(overallScale.certificateName())
                .overallScore(overall)
                .minScore(overallScale.minScore())
                .maxScore(overallScale.maxScore())
                .step(overallScale.step())
                .cefrLevel(cefr(overallScale, overall))
                .skills(skills)
                .build();
    }

    static String skillOf(ExamSection section) {
        String text = section == null ? "" : ((section.getSectionType() == null ? "" : section.getSectionType()) + " "
                + (section.getTitle() == null ? "" : section.getTitle())).toUpperCase();
        if (text.contains("LISTEN")) return "LISTENING";
        if (text.contains("READ")) return "READING";
        if (text.contains("WRIT")) return "WRITING";
        if (text.contains("SPEAK")) return "SPEAKING";
        return "OTHER";
    }

    /** Tỷ lệ 0 – 1 → điểm theo thang, làm tròn theo bước. */
    static BigDecimal toScale(BigDecimal ratio, ScoringScale scale) {
        BigDecimal range = scale.maxScore().subtract(scale.minScore());
        return scale.normalize(scale.minScore().add(range.multiply(ratio)));
    }

    static BigDecimal ratioOf(BigDecimal score, ScoringScale scale) {
        BigDecimal range = scale.maxScore().subtract(scale.minScore());
        return range.signum() <= 0 ? BigDecimal.ZERO
                : score.subtract(scale.minScore()).divide(range, 6, RoundingMode.HALF_UP);
    }

    /** Điểm theo thang → CEFR (bảng quy đổi phổ biến của từng chứng chỉ; chứng chỉ khác quy theo %). */
    static String cefr(ScoringScale scale, BigDecimal score) {
        String name = scale.certificateName() == null ? "" : scale.certificateName().toUpperCase();
        double s = score.doubleValue();
        double max = scale.maxScore().doubleValue();
        if (name.contains("IELTS") && max == 9) {
            return s >= 8.5 ? "C2" : s >= 7 ? "C1" : s >= 5.5 ? "B2" : s >= 4 ? "B1" : s >= 3 ? "A2" : "A1";
        }
        if (name.contains("TOEIC") && max >= 900) {
            return s >= 945 ? "C1" : s >= 785 ? "B2" : s >= 550 ? "B1" : s >= 225 ? "A2" : "A1";
        }
        if (name.contains("VSTEP") && max == 10) {
            return s >= 8.5 ? "C1" : s >= 6 ? "B2" : s >= 4 ? "B1" : s >= 2 ? "A2" : "A1";
        }
        if (name.contains("TOEFL") && max == 120) {
            return s >= 114 ? "C2" : s >= 95 ? "C1" : s >= 72 ? "B2" : s >= 42 ? "B1" : s >= 20 ? "A2" : "A1";
        }
        double p = ratioOf(score, scale).doubleValue() * 100;
        return p >= 90 ? "C2" : p >= 80 ? "C1" : p >= 65 ? "B2" : p >= 50 ? "B1" : p >= 35 ? "A2" : "A1";
    }
}
