package com.sba301.englishcenter.common.enums;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * Thang điểm chuẩn của các chứng chỉ đang hỗ trợ.
 * <p>
 * Dùng làm giá trị mặc định khi tạo chứng chỉ và là nguồn duy nhất cho
 * khoảng điểm / bước điểm ở tầng entity. Các giá trị này vẫn có thể bị
 * admin ghi đè — cột {@code certificates.standard_scale} chỉ đánh dấu
 * chứng chỉ đang dựa trên thang chuẩn nào.
 */
@Getter
public enum StandardCertificateScale {

    /** IELTS dùng trong hệ thống: 1.0 – 9.0, bước 0.5 (band 0 không dùng để đặt mục tiêu). */
    IELTS("IELTS", new BigDecimal("1.0"), new BigDecimal("9.0"), new BigDecimal("0.5"),
            "Band", "AVERAGE", "IELTS"),

    /** TOEIC Listening & Reading: 10 – 990, bước 5; Speaking & Writing là thang riêng nên không khớp. */
    TOEIC("TOEIC", new BigDecimal("10"), new BigDecimal("990"), new BigDecimal("5"),
            "Điểm", "SUM", "STANDARD"),

    /** TOEFL iBT thang 0 – 120: 4 kỹ năng 0 – 30, tổng = 120, bước 1. */
    TOEFL("TOEFL", BigDecimal.ZERO, new BigDecimal("120"), BigDecimal.ONE,
            "Điểm", "SUM", "STANDARD"),

    /** VSTEP.3–5: 0 – 10, bước 0.5. */
    VSTEP("VSTEP", BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("0.5"),
            "Điểm", "AVERAGE", "VSTEP");

    private final String code;
    private final BigDecimal minScore;
    private final BigDecimal maxScore;
    private final BigDecimal scoreStep;
    private final String scoreUnit;
    private final String scaleType;
    private final String roundingRule;

    StandardCertificateScale(String code,
                             BigDecimal minScore,
                             BigDecimal maxScore,
                             BigDecimal scoreStep,
                             String scoreUnit,
                             String scaleType,
                             String roundingRule) {
        this.code = code;
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.scoreStep = scoreStep;
        this.scoreUnit = scoreUnit;
        this.scaleType = scaleType;
        this.roundingRule = roundingRule;
    }

    /**
     * Nhận diện thang chuẩn từ tên chứng chỉ.
     * <p>
     * TOEIC Speaking & Writing và TOEFL iBT thang 1–6 là thang riêng,
     * không áp dụng thang chuẩn tương ứng.
     */
    public static Optional<StandardCertificateScale> detect(String certificateName) {
        if (certificateName == null || certificateName.isBlank()) {
            return Optional.empty();
        }
        String name = certificateName.trim().toUpperCase(Locale.ROOT);

        if (name.contains("IELTS")) {
            return Optional.of(IELTS);
        }
        if (name.contains("TOEIC")) {
            if (name.contains("SPEAKING") || name.contains("WRITING") || name.contains("S&W")) {
                return Optional.empty();
            }
            return Optional.of(TOEIC);
        }
        if (name.contains("TOEFL")) {
            if (name.contains("1-6") || name.contains("1–6") || name.contains("THANG 1")
                    || name.contains("2026") || name.contains("MỚI")) {
                return Optional.empty();
            }
            return Optional.of(TOEFL);
        }
        if (name.contains("VSTEP")) {
            return Optional.of(VSTEP);
        }
        return Optional.empty();
    }

    public static Optional<StandardCertificateScale> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
