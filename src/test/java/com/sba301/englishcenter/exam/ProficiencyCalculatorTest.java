package com.sba301.englishcenter.exam;

import com.sba301.englishcenter.dto.response.ProficiencyResponse;
import com.sba301.englishcenter.dto.response.SectionScoreResponse;
import com.sba301.englishcenter.service.ProficiencyCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quy đổi điểm bài kiểm tra năng lực sang thang IELTS / TOEIC / VSTEP.
 */
class ProficiencyCalculatorTest {

    private final ProficiencyCalculator calculator = new ProficiencyCalculator();

    private static SectionScoreResponse section(String type, double percentage, boolean pending) {
        return SectionScoreResponse.builder()
                .sectionType(type).title(type).percentage(BigDecimal.valueOf(percentage))
                .pendingManualGrading(pending).build();
    }

    private static List<String> scores(ProficiencyCalculator.Assessment a) {
        return a.skills().stream().map(ProficiencyResponse.SkillScore::getScore)
                .map(s -> s == null ? null : s.toPlainString()).toList();
    }

    @Test
    void ieltsBandsAndOverallUseOfficialRounding() {
        // Listening 1/2, Reading 2/5, Writing 4/6 -> 4.5 / 3.5 / 6.0, trung bình 4.67 -> 4.5
        ProficiencyCalculator.Assessment a = calculator.assess("IELTS", List.of(
                section("LISTENING", 50, false), section("READING", 40, false), section("WRITING", 66.67, false)), null);

        assertThat(scores(a)).containsExactly("4.5", "3.5", "6");
        assertThat(a.overall()).isEqualByComparingTo("4.5");
        assertThat(a.level()).isEqualTo("Limited user");
        assertThat(a.cefr()).isEqualTo("B1");
        assertThat(a.pendingGrading()).isFalse();
        // Luật làm tròn IELTS: .25 lên .5, .75 lên số nguyên, dưới .25 xuống
        assertThat(ProficiencyCalculator.roundHalfBand(6.125)).isEqualTo(6.0);
        assertThat(ProficiencyCalculator.roundHalfBand(6.25)).isEqualTo(6.5);
        assertThat(ProficiencyCalculator.roundHalfBand(6.75)).isEqualTo(7.0);
    }

    @Test
    void toeicTotalIsListeningPlusReading() {
        ProficiencyCalculator.Assessment a = calculator.assess("TOEIC",
                List.of(section("LISTENING", 80, false), section("READING", 60, false)), null);

        assertThat(scores(a)).containsExactly("395", "300");
        assertThat(a.overall()).isEqualByComparingTo("695");
        assertThat(a.overallMax()).isEqualByComparingTo("990");
        assertThat(a.level()).isEqualTo("TOEIC 695");
        assertThat(a.skills().get(0).getCefr()).isEqualTo("B1");
    }

    @Test
    void vstepAverageGivesLevel() {
        ProficiencyCalculator.Assessment a = calculator.assess("VSTEP", List.of(
                section("LISTENING", 70, false), section("READING", 60, false),
                section("WRITING", 60, false), section("SPEAKING", 60, false)), null);

        assertThat(scores(a)).containsExactly("7", "6", "6", "6");
        assertThat(a.overall()).isEqualByComparingTo("6.5");
        assertThat(a.level()).isEqualTo("Bậc 4 (B2)");
        assertThat(a.cefr()).isEqualTo("B2");
    }

    @Test
    void pendingWritingHasNoOverallYet() {
        ProficiencyCalculator.Assessment a = calculator.assess("IELTS Academic",
                List.of(section("READING", 75, false), section("WRITING", 0, true)), null);

        assertThat(a.pendingGrading()).isTrue();
        assertThat(a.overall()).isNull();
        assertThat(a.level()).isNull();
        assertThat(scores(a)).containsExactly("7", null);
    }

    @Test
    void otherCertificateKeepsPercentage() {
        ProficiencyCalculator.Assessment a = calculator.assess("Cambridge", List.of(), BigDecimal.valueOf(72.5));

        assertThat(a.scale()).isEqualTo(ProficiencyCalculator.Scale.RAW);
        assertThat(a.overall()).isEqualByComparingTo("72.5");
        assertThat(a.level()).isEqualTo("73%");
    }
}
