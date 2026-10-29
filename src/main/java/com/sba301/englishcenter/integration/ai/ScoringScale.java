package com.sba301.englishcenter.integration.ai;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Thang điểm AI dùng để chấm: lấy theo chứng chỉ mục tiêu của người học.
 * VD IELTS 0 – 9 (bước 0.5), TOEIC Speaking/Writing 0 – 200 (bước 10), VSTEP 0 – 10 (bước 0.5).
 *
 * @param certificateName tên chứng chỉ hiển thị trong prompt (VD "IELTS Academic")
 * @param minScore        điểm thấp nhất của kỹ năng
 * @param maxScore        điểm cao nhất của kỹ năng
 * @param step            bước điểm
 */
public record ScoringScale(String certificateName, BigDecimal minScore, BigDecimal maxScore, BigDecimal step) {

    /** Mặc định khi người học chưa chọn chứng chỉ mục tiêu / chứng chỉ chưa cấu hình thang. */
    public static final ScoringScale IELTS = new ScoringScale("IELTS",
            BigDecimal.ZERO, new BigDecimal("9.0"), new BigDecimal("0.5"));

    /** Kẹp trong [min, max] rồi làm tròn tới bước gần nhất. */
    public BigDecimal normalize(BigDecimal score) {
        BigDecimal clamped = score.max(minScore).min(maxScore);
        BigDecimal steps = clamped.subtract(minScore).divide(step, 0, RoundingMode.HALF_UP);
        BigDecimal rounded = minScore.add(steps.multiply(step));
        return rounded.min(maxScore).setScale(Math.max(step.stripTrailingZeros().scale(), 0), RoundingMode.HALF_UP);
    }
}
