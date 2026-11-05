package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.dto.practice.CriterionScoreData;
import com.sba301.englishcenter.dto.practice.EssayAiEvaluationData;
import com.sba301.englishcenter.dto.response.PracticeLevelResponse;
import com.sba301.englishcenter.entity.ExamAnswer;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.entity.ExamSection;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.service.ExamGrader;
import com.sba301.englishcenter.util.JsonbMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Quy kết quả một lượt làm đề luyện tập ra trình độ theo chứng chỉ mục tiêu của học viên.
 * <ol>
 *     <li>Gộp các section cùng kỹ năng (VD Listening Part 1 + Part 2) → % điểm của kỹ năng;</li>
 *     <li>% → điểm theo thang của phần thi đó (Writing / Speaking: % = band AI chấm quy ra, nên khớp band AI);</li>
 *     <li>Kỹ năng bỏ trống toàn bộ (không trả lời câu nào) → 0 điểm, giống IELTS band 0 "không làm bài",
 *     thay vì điểm thấp nhất của thang (IELTS 1.0);</li>
 *     <li>Điểm tổng = trung bình tỷ lệ các kỹ năng quy ra thang chung, làm tròn theo bước (IELTS: 0.5);
 *     bỏ trống tất cả kỹ năng → 0;</li>
 *     <li>Phần Core (section GENERAL, VD Aptis Grammar &amp; Vocabulary): chấm theo thang của phần đó; nếu chứng chỉ
 *     đánh dấu excludeFromTotal thì chỉ báo điểm riêng, không tính vào điểm tổng;</li>
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
        // Dev 5: thống kê từng part (section) + nhận xét AI của các câu tự luận, để giải thích điểm
        Map<String, Map<String, PartStat>> partsBySkill = new LinkedHashMap<>();
        Map<String, List<EssayAiEvaluationData>> essaysBySkill = new LinkedHashMap<>();
        for (ExamQuestion eq : examQuestions) {
            ExamAnswer answer = answers.get(eq.getQuestion().getQuestionId());
            if (grader.isPendingManual(eq.getQuestion(), answer)) {
                return null;
            }
            // [điểm đạt, điểm tối đa, số câu có trả lời] của kỹ năng; map đã có sẵn khóa theo thứ tự L - R - W - S
            String skill = skillOf(eq.getSection());
            BigDecimal[] acc = bySkill.get(skill);
            if (acc == null) {
                acc = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
                bySkill.put(skill, acc);
            }
            acc[0] = acc[0].add(answer != null && answer.getEarnedScore() != null ? answer.getEarnedScore() : BigDecimal.ZERO);
            acc[1] = acc[1].add(ExamGrader.points(eq.getQuestion()));
            boolean answered = grader.isAnswered(eq.getQuestion(), answer);
            if (answered) {
                acc[2] = acc[2].add(BigDecimal.ONE);
            }

            PartStat part = partsBySkill.computeIfAbsent(skill, k -> new LinkedHashMap<>())
                    .computeIfAbsent(partName(eq.getSection(), skill), PartStat::new);
            part.questions++;
            part.answered += answered ? 1 : 0;
            part.earned = part.earned.add(answer != null && answer.getEarnedScore() != null ? answer.getEarnedScore() : BigDecimal.ZERO);
            part.max = part.max.add(ExamGrader.points(eq.getQuestion()));
            if (grader.isObjective(eq.getQuestion().getQuestionType())) {
                part.objective++;
                part.correct += answer != null && Boolean.TRUE.equals(answer.getIsCorrect()) ? 1 : 0;
            } else if (answer != null && answer.getAiEvaluation() != null) {
                EssayAiEvaluationData ai = JsonbMapper.read(answer.getAiEvaluation(), EssayAiEvaluationData.class);
                if (ai != null) {
                    essaysBySkill.computeIfAbsent(skill, k -> new ArrayList<>()).add(ai);
                }
            }
        }

        ScoringScale overallScale = scoringScaleResolver.resolve(studentId, (String) null);
        List<PracticeLevelResponse.SkillLevel> skills = new ArrayList<>();
        BigDecimal ratioSum = BigDecimal.ZERO;
        int counted = 0;
        boolean anyAnswered = false;
        List<String> blankSkills = new ArrayList<>();
        Optional<ScoringScaleResolver.CorePart> core = bySkill.get("OTHER") == null
                ? Optional.empty() : scoringScaleResolver.resolveCorePart(studentId);
        // Đề chỉ có phần Core thì vẫn lấy Core làm điểm tổng (không để điểm tổng trống)
        boolean onlyCore = bySkill.entrySet().stream()
                .noneMatch(e -> !"OTHER".equals(e.getKey()) && e.getValue() != null && e.getValue()[1].signum() > 0);
        for (Map.Entry<String, BigDecimal[]> e : bySkill.entrySet()) {
            if (e.getValue() == null || e.getValue()[1].signum() <= 0) {
                continue;
            }
            BigDecimal ratio = e.getValue()[0].divide(e.getValue()[1], 6, RoundingMode.HALF_UP);
            boolean isCore = "OTHER".equals(e.getKey());
            ScoringScale scale = isCore ? core.map(ScoringScaleResolver.CorePart::scale).orElse(overallScale)
                    : scoringScaleResolver.resolve(studentId, e.getKey());
            boolean excluded = isCore && !onlyCore && core.map(ScoringScaleResolver.CorePart::excludeFromTotal).orElse(false);
            // Không trả lời câu nào của kỹ năng -> 0 điểm (không kéo lên điểm thấp nhất của thang)
            boolean blank = e.getValue()[2].signum() == 0;
            BigDecimal score = blank ? zeroScore(scale) : toScale(ratio, scale);
            if (!excluded) {
                ratioSum = ratioSum.add(blank ? BigDecimal.ZERO : ratioOf(score, scale));
                counted++;
                anyAnswered |= !blank;
            }
            String name = isCore ? core.map(ScoringScaleResolver.CorePart::name).orElse(null) : null;
            if (blank) {
                blankSkills.add(name != null ? name : skillLabel(e.getKey()));
            }

            List<PartStat> partStats = new ArrayList<>(partsBySkill.getOrDefault(e.getKey(), Map.of()).values());
            int questionCount = partStats.stream().mapToInt(p -> p.questions).sum();
            int answeredCount = partStats.stream().mapToInt(p -> p.answered).sum();
            int objectiveCount = partStats.stream().mapToInt(p -> p.objective).sum();
            int correctCount = partStats.stream().mapToInt(p -> p.correct).sum();
            skills.add(PracticeLevelResponse.SkillLevel.builder()
                    .skill(e.getKey())
                    .score(score)
                    .maxScore(scale.maxScore())
                    .cefrLevel(cefr(scale, score))
                    .percentage(ExamGrader.percentage(e.getValue()[0], e.getValue()[1]))
                    .name(name)
                    .excludeFromTotal(excluded ? Boolean.TRUE : null)
                    .questionCount(questionCount)
                    .answeredCount(answeredCount)
                    .correctCount(objectiveCount > 0 ? correctCount : null)
                    .comment(skillComment(blank, questionCount, answeredCount, objectiveCount, correctCount,
                            partStats, essaysBySkill.getOrDefault(e.getKey(), List.of())))
                    .parts(partStats.stream().map(PartStat::toResult).toList())
                    .build());
        }
        if (skills.isEmpty()) {
            return null;
        }

        BigDecimal overall = anyAnswered && counted > 0
                ? toScale(ratioSum.divide(BigDecimal.valueOf(counted), 6, RoundingMode.HALF_UP), overallScale)
                : zeroScore(overallScale);
        return PracticeLevelResponse.builder()
                .certificateName(overallScale.certificateName())
                .overallScore(overall)
                .minScore(overallScale.minScore())
                .maxScore(overallScale.maxScore())
                .step(overallScale.step())
                .cefrLevel(cefr(overallScale, overall))
                .skills(skills)
                .summary(overallSummary(overallScale, overall, skills, blankSkills))
                .build();
    }

    // ===================== Dev 5: giải thích điểm =====================

    /** Thống kê 1 part (section) trong kỹ năng. */
    private static final class PartStat {
        private final String name;
        private int questions;
        private int answered;
        private int objective;
        private int correct;
        private BigDecimal earned = BigDecimal.ZERO;
        private BigDecimal max = BigDecimal.ZERO;

        private PartStat(String name) {
            this.name = name;
        }

        private PracticeLevelResponse.PartResult toResult() {
            return PracticeLevelResponse.PartResult.builder()
                    .name(name)
                    .questionCount(questions)
                    .answeredCount(answered)
                    .correctCount(objective > 0 ? correct : null)
                    .percentage(ExamGrader.percentage(earned, max))
                    .build();
        }
    }

    private static String partName(ExamSection section, String skill) {
        if (section != null && section.getTitle() != null && !section.getTitle().isBlank()) {
            return section.getTitle().trim();
        }
        return skillLabel(skill);
    }

    static String skillLabel(String skill) {
        return switch (skill) {
            case "LISTENING" -> "Listening";
            case "READING" -> "Reading";
            case "WRITING" -> "Writing";
            case "SPEAKING" -> "Speaking";
            default -> "Core";
        };
    }

    /** Nhận xét 1 kỹ năng: đúng bao nhiêu câu, bỏ trống bao nhiêu, yếu ở part / tiêu chí nào. */
    private static String skillComment(boolean blank, int questionCount, int answeredCount, int objectiveCount,
                                       int correctCount, List<PartStat> parts, List<EssayAiEvaluationData> essays) {
        if (blank) {
            return "Bạn không trả lời câu nào của kỹ năng này nên được 0 điểm.";
        }
        StringBuilder sb = new StringBuilder();
        if (objectiveCount > 0) {
            sb.append("Đúng ").append(correctCount).append("/").append(objectiveCount).append(" câu");
            if (answeredCount < questionCount) {
                sb.append(", bỏ trống ").append(questionCount - answeredCount).append(" câu");
            }
            sb.append(".");
            // Part yếu nhất (tỷ lệ đúng thấp nhất) khi kỹ năng có từ 2 part trở lên
            parts.stream()
                    .filter(p -> p.objective > 0)
                    .min((a, b) -> Double.compare((double) a.correct / a.objective, (double) b.correct / b.objective))
                    .filter(p -> parts.size() > 1 && p.correct < p.objective)
                    .ifPresent(p -> sb.append(" Yếu nhất: ").append(p.name).append(" (đúng ")
                            .append(p.correct).append("/").append(p.objective).append(" câu)."));
        }
        for (EssayAiEvaluationData ai : essays) {
            CriterionScoreData weakest = weakestCriterion(ai);
            if (weakest != null && weakest.comment() != null && !weakest.comment().isBlank()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append("Tiêu chí thấp nhất (").append(weakest.band().toPlainString()).append("): ")
                        .append(weakest.comment());
            } else if (ai.feedback() != null && !ai.feedback().isBlank()) {
                if (!sb.isEmpty()) sb.append(" ");
                sb.append(ai.feedback());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static CriterionScoreData weakestCriterion(EssayAiEvaluationData ai) {
        CriterionScoreData weakest = null;
        for (CriterionScoreData c : new CriterionScoreData[]{ai.taskAchievement(), ai.coherenceCohesion(),
                ai.lexicalResource(), ai.grammaticalAccuracy()}) {
            if (c != null && c.band() != null && (weakest == null || c.band().compareTo(weakest.band()) < 0)) {
                weakest = c;
            }
        }
        return weakest;
    }

    /** Giải thích điểm tổng: trung bình các kỹ năng, kỹ năng thấp nhất, kỹ năng bỏ trống. */
    private static List<String> overallSummary(ScoringScale scale, BigDecimal overall,
                                               List<PracticeLevelResponse.SkillLevel> skills, List<String> blankSkills) {
        List<String> lines = new ArrayList<>();
        List<PracticeLevelResponse.SkillLevel> counted = skills.stream()
                .filter(s -> !Boolean.TRUE.equals(s.getExcludeFromTotal()))
                .toList();
        StringBuilder avg = new StringBuilder("Điểm tổng ").append(overall.toPlainString())
                .append(" = trung bình ").append(counted.size()).append(" kỹ năng (");
        for (int i = 0; i < counted.size(); i++) {
            PracticeLevelResponse.SkillLevel s = counted.get(i);
            if (i > 0) avg.append(", ");
            avg.append(s.getName() != null ? s.getName() : skillLabel(s.getSkill())).append(" ")
                    .append(s.getScore().toPlainString());
        }
        avg.append("), làm tròn theo bước ").append(scale.step().stripTrailingZeros().toPlainString())
                .append(" của thang ").append(scale.certificateName()).append(".");
        lines.add(avg.toString());
        if (!blankSkills.isEmpty()) {
            lines.add("Bỏ trống " + String.join(", ", blankSkills) + " nên các kỹ năng này tính 0 điểm và kéo điểm tổng xuống.");
        }
        counted.stream()
                .filter(s -> s.getScore() != null)
                .min((a, b) -> ratioOfSkill(a).compareTo(ratioOfSkill(b)))
                .filter(s -> counted.size() > 1)
                .ifPresent(s -> lines.add("Kỹ năng cần cải thiện nhất: "
                        + (s.getName() != null ? s.getName() : skillLabel(s.getSkill())) + " ("
                        + s.getScore().toPlainString() + "/" + s.getMaxScore().toPlainString() + ")."));
        return lines;
    }

    private static BigDecimal ratioOfSkill(PracticeLevelResponse.SkillLevel s) {
        return s.getMaxScore() == null || s.getMaxScore().signum() <= 0 ? BigDecimal.ZERO
                : s.getScore().divide(s.getMaxScore(), 6, RoundingMode.HALF_UP);
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

    /** 0 điểm, cùng số chữ số thập phân với thang (IELTS: 0.0, TOEIC: 0). */
    static BigDecimal zeroScore(ScoringScale scale) {
        return BigDecimal.ZERO.setScale(Math.max(scale.step().stripTrailingZeros().scale(), 0), RoundingMode.HALF_UP);
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
