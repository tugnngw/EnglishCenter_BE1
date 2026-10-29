package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.ProficiencyResponse;
import com.sba301.englishcenter.dto.response.SectionScoreResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dev 3 - Quy đổi điểm thô của bài kiểm tra năng lực sang thang của chứng chỉ.
 * <p>
 * Mỗi phần thi (section) có tỷ lệ đúng 0-100% -> điểm kỹ năng theo thang:
 * <ul>
 *   <li>IELTS : band 0-9 bước 0.5 mỗi kỹ năng; overall = trung bình, làm tròn theo luật IELTS
 *       (lẻ &lt; .25 -> .0, &lt; .75 -> .5, còn lại lên số nguyên); CEFR theo bảng quy đổi của IELTS.</li>
 *   <li>TOEIC : Listening / Reading 5-495 bước 5, tổng = L + R (10-990); Speaking / Writing 0-200;
 *       CEFR từng kỹ năng theo ngưỡng của ETS.</li>
 *   <li>VSTEP : 0-10 bước 0.5 mỗi kỹ năng; điểm chung = trung bình làm tròn 0.5; bậc B1 / B2 / C1.</li>
 *   <li>Chứng chỉ khác: giữ % của đề.</li>
 * </ul>
 * Loại thang nhận diện theo tên chứng chỉ (giống FE {@code examScales.ts}). Phần còn chờ mentor chấm
 * thì chưa có điểm và chưa tính điểm tổng.
 */
@Component
public class ProficiencyCalculator {

    public enum Scale { IELTS, TOEIC, VSTEP, RAW }

    public enum Skill { LISTENING, READING, WRITING, SPEAKING, OTHER }

    private record Range(double min, double max, double step) {
    }

    public static Scale detectScale(String certificateName) {
        String name = certificateName == null ? "" : certificateName.toUpperCase(Locale.ROOT);
        if (name.contains("IELTS")) return Scale.IELTS;
        if (name.contains("TOEIC")) return Scale.TOEIC;
        if (name.contains("VSTEP")) return Scale.VSTEP;
        return Scale.RAW;
    }

    public static Skill toSkill(String sectionType) {
        String t = sectionType == null ? "" : sectionType.toUpperCase(Locale.ROOT);
        if (t.contains("LISTEN")) return Skill.LISTENING;
        if (t.contains("READ")) return Skill.READING;
        if (t.contains("WRIT")) return Skill.WRITING;
        if (t.contains("SPEAK")) return Skill.SPEAKING;
        return Skill.OTHER;
    }

    private static Range range(Scale scale, Skill skill) {
        return switch (scale) {
            case IELTS -> new Range(0, 9, 0.5);
            case TOEIC -> skill == Skill.LISTENING || skill == Skill.READING
                    ? new Range(5, 495, 5) : new Range(0, 200, 10);
            case VSTEP -> new Range(0, 10, 0.5);
            case RAW -> new Range(0, 100, 1);
        };
    }

    /** Tỷ lệ đúng (0-100) -> điểm kỹ năng theo thang, làm tròn theo bước của thang. */
    public static double skillScore(Scale scale, Skill skill, double percentage) {
        Range r = range(scale, skill);
        double p = Math.max(0, Math.min(100, percentage)) / 100.0;
        double raw = r.min() + p * (r.max() - r.min());
        double rounded = Math.round(raw / r.step()) * r.step();
        return Math.max(r.min(), Math.min(r.max(), rounded));
    }

    /** Làm tròn trung bình về bước 0.5 (đúng luật IELTS và VSTEP: .25 lên .5, .75 lên số nguyên). */
    public static double roundHalfBand(double value) {
        return Math.round(value * 2) / 2.0;
    }

    public static String ieltsCefr(double band) {
        if (band >= 8.5) return "C2";
        if (band >= 7.0) return "C1";
        if (band >= 5.5) return "B2";
        if (band >= 4.0) return "B1";
        return "A2";
    }

    public static String ieltsLevel(double band) {
        if (band >= 9.0) return "Expert user";
        if (band >= 8.0) return "Very good user";
        if (band >= 7.0) return "Good user";
        if (band >= 6.0) return "Competent user";
        if (band >= 5.0) return "Modest user";
        if (band >= 4.0) return "Limited user";
        return "Extremely limited user";
    }

    public static String vstepLevel(double score) {
        if (score >= 8.5) return "Bậc 5 (C1)";
        if (score >= 6.0) return "Bậc 4 (B2)";
        if (score >= 4.0) return "Bậc 3 (B1)";
        return "Chưa đạt bậc 3";
    }

    public static String vstepCefr(double score) {
        if (score >= 8.5) return "C1";
        if (score >= 6.0) return "B2";
        if (score >= 4.0) return "B1";
        return null;
    }

    /** Ngưỡng CEFR của ETS cho TOEIC Listening / Reading. */
    public static String toeicCefr(Skill skill, double score) {
        if (skill == Skill.LISTENING) {
            if (score >= 490) return "C1";
            if (score >= 400) return "B2";
            if (score >= 275) return "B1";
            if (score >= 110) return "A2";
            if (score >= 60) return "A1";
            return null;
        }
        if (skill == Skill.READING) {
            if (score >= 455) return "C1";
            if (score >= 385) return "B2";
            if (score >= 275) return "B1";
            if (score >= 115) return "A2";
            if (score >= 60) return "A1";
            return null;
        }
        return null;
    }

    /**
     * Tính năng lực từ điểm các phần thi. Đề không chia section thì dùng % cả bài như một kỹ năng chung.
     *
     * @param overallPercentage % cả bài (dùng khi không có section và cho thang RAW)
     */
    public Assessment assess(String certificateName, List<SectionScoreResponse> sections, BigDecimal overallPercentage) {
        Scale scale = detectScale(certificateName);
        List<ProficiencyResponse.SkillScore> skills = new ArrayList<>();
        boolean pending = false;

        List<SectionScoreResponse> source = sections == null ? List.of() : sections;
        if (source.isEmpty()) {
            double p = overallPercentage == null ? 0 : overallPercentage.doubleValue();
            skills.add(skill(scale, Skill.OTHER, "Toàn bài", p, false));
        } else {
            for (SectionScoreResponse s : source) {
                boolean isPending = Boolean.TRUE.equals(s.getPendingManualGrading());
                pending |= isPending;
                double p = s.getPercentage() == null ? 0 : s.getPercentage().doubleValue();
                skills.add(skill(scale, toSkill(s.getSectionType()), s.getTitle(), p, isPending));
            }
        }

        if (pending) {
            return new Assessment(scale, skills, null, overallMax(scale, skills), null, null, true);
        }
        List<ProficiencyResponse.SkillScore> scored = skills.stream().filter(k -> k.getScore() != null).toList();
        return switch (scale) {
            case IELTS -> {
                double band = roundHalfBand(average(scored));
                yield new Assessment(scale, skills, bd(band), BigDecimal.valueOf(9), ieltsLevel(band), ieltsCefr(band), false);
            }
            case VSTEP -> {
                double score = roundHalfBand(average(scored));
                yield new Assessment(scale, skills, bd(score), BigDecimal.TEN, vstepLevel(score), vstepCefr(score), false);
            }
            case TOEIC -> {
                List<ProficiencyResponse.SkillScore> lr = scored.stream()
                        .filter(k -> "LISTENING".equals(k.getSkill()) || "READING".equals(k.getSkill())).toList();
                List<ProficiencyResponse.SkillScore> basis = lr.isEmpty() ? scored : lr;
                double total = basis.stream().mapToDouble(k -> k.getScore().doubleValue()).sum();
                yield new Assessment(scale, skills, bd(total), overallMax(scale, skills),
                        "TOEIC " + Math.round(total), null, false);
            }
            case RAW -> {
                double p = overallPercentage == null ? average(scored) : overallPercentage.doubleValue();
                yield new Assessment(scale, skills, bd(p), BigDecimal.valueOf(100), Math.round(p) + "%", null, false);
            }
        };
    }

    private ProficiencyResponse.SkillScore skill(Scale scale, Skill skill, String title, double percentage, boolean pending) {
        Range r = range(scale, skill);
        Double score = pending ? null : skillScore(scale, skill, percentage);
        String cefr = null;
        if (score != null) {
            cefr = switch (scale) {
                case IELTS -> ieltsCefr(score);
                case TOEIC -> toeicCefr(skill, score);
                case VSTEP -> vstepCefr(score);
                case RAW -> null;
            };
        }
        return ProficiencyResponse.SkillScore.builder()
                .skill(skill.name())
                .title(title)
                .score(score == null ? null : bd(score))
                .maxScore(bd(r.max()))
                .percentage(bd(percentage))
                .pendingManualGrading(pending)
                .cefr(cefr)
                .build();
    }

    private static BigDecimal overallMax(Scale scale, List<ProficiencyResponse.SkillScore> skills) {
        return switch (scale) {
            case IELTS -> BigDecimal.valueOf(9);
            case VSTEP -> BigDecimal.TEN;
            case TOEIC -> {
                long lr = skills.stream().filter(k -> "LISTENING".equals(k.getSkill()) || "READING".equals(k.getSkill())).count();
                yield BigDecimal.valueOf(lr == 0 ? 990 : lr * 495);
            }
            case RAW -> BigDecimal.valueOf(100);
        };
    }

    private static double average(List<ProficiencyResponse.SkillScore> skills) {
        return skills.stream().mapToDouble(k -> k.getScore().doubleValue()).average().orElse(0);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Kết quả quy đổi; overall / level null khi còn phần chờ mentor chấm. */
    public record Assessment(Scale scale, List<ProficiencyResponse.SkillScore> skills, BigDecimal overall,
                             BigDecimal overallMax, String level, String cefr, boolean pendingGrading) {
    }
}
