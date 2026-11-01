package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.integration.ai.ScoringScale;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quy % ra thang chứng chỉ và CEFR của đề luyện tập.
 */
class PracticeLevelCalculatorTest {

    private static final ScoringScale TOEIC = new ScoringScale("TOEIC Listening & Reading",
            new BigDecimal("10"), new BigDecimal("990"), new BigDecimal("5"));
    private static final ScoringScale VSTEP = new ScoringScale("VSTEP",
            BigDecimal.ZERO, BigDecimal.TEN, new BigDecimal("0.5"));

    @Test
    void ratioIsMappedToScaleAndRoundedToStep() {
        assertThat(PracticeLevelCalculator.toScale(new BigDecimal("0.70"), ScoringScale.IELTS))
                .isEqualByComparingTo("6.5"); // 6.3 -> 6.5
        assertThat(PracticeLevelCalculator.toScale(new BigDecimal("0.60"), TOEIC))
                .isEqualByComparingTo("600"); // 10 + 0.6 * 980 = 598 -> 600
    }

    @Test
    void cefrFollowsEachCertificateTable() {
        assertThat(PracticeLevelCalculator.cefr(ScoringScale.IELTS, new BigDecimal("6.0"))).isEqualTo("B2");
        assertThat(PracticeLevelCalculator.cefr(ScoringScale.IELTS, new BigDecimal("3.0"))).isEqualTo("A2");
        assertThat(PracticeLevelCalculator.cefr(TOEIC, new BigDecimal("600"))).isEqualTo("B1");
        assertThat(PracticeLevelCalculator.cefr(VSTEP, new BigDecimal("6.5"))).isEqualTo("B2");
    }
}
