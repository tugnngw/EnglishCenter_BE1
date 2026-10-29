package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.integration.ai.ScoringScale;
import com.sba301.englishcenter.repository.CertificatePartRepository;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Lấy thang điểm AI chấm theo chứng chỉ mục tiêu của người học (TASK-D5-04).
 * Thứ tự ưu tiên:
 *   1. Part của chứng chỉ trùng kỹ năng (VD part "Writing" của TOEIC S&W: 0 – 200, bước 10) — certificate_parts.properties
 *   2. Thang chung của chứng chỉ — JSON {"scoring": {...}} trong certificates.description (trang Chứng chỉ lưu)
 *   3. Mặc định IELTS 0 – 9 khi chưa chọn chứng chỉ mục tiêu / chứng chỉ chưa cấu hình thang
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoringScaleResolver {

    private final ObjectMapper json = new ObjectMapper();
    private final StudentProfileRepository studentProfileRepository;
    private final CertificateRepository certificateRepository;
    private final CertificatePartRepository certificatePartRepository;

    @Transactional(readOnly = true)
    public ScoringScale resolve(UUID studentId, PracticeExamType skill) {
        Optional<Certificate> certificate = studentProfileRepository.findById(studentId)
                .map(StudentProfile::getTargetCertificateId)
                .flatMap(certificateRepository::findById);
        if (certificate.isEmpty()) {
            return ScoringScale.IELTS;
        }
        Certificate cert = certificate.get();
        String name = cert.getCertificateName();

        // 1. Part trùng kỹ năng
        for (CertificatePart part : certificatePartRepository
                .findByCertificate_CertificateIdOrderByPartOrderAsc(cert.getCertificateId())) {
            if (part.getPartName() != null && part.getPartName().toUpperCase().contains(skill.name())) {
                ScoringScale scale = fromJson(name, part.getProperties());
                if (scale != null) {
                    return scale;
                }
            }
        }
        // 2. Thang chung của chứng chỉ
        ScoringScale scale = fromJson(name, scoringNodeOf(cert.getDescription()));
        return scale != null ? scale : new ScoringScale(name, ScoringScale.IELTS.minScore(),
                ScoringScale.IELTS.maxScore(), ScoringScale.IELTS.step());
    }

    private String scoringNodeOf(String description) {
        if (!StringUtils.hasText(description) || !description.trim().startsWith("{")) {
            return null;
        }
        try {
            JsonNode scoring = json.readTree(description).path("scoring");
            return scoring.isObject() ? scoring.toString() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Đọc {"minScore", "maxScore", "step"}; thiếu / sai thì trả null để dùng mức ưu tiên sau. */
    private ScoringScale fromJson(String certificateName, String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode node = json.readTree(raw);
            BigDecimal max = number(node.path("maxScore"));
            if (max == null || max.signum() <= 0) {
                return null;
            }
            BigDecimal min = Optional.ofNullable(number(node.path("minScore"))).orElse(BigDecimal.ZERO);
            BigDecimal step = Optional.ofNullable(number(node.path("step"))).filter(s -> s.signum() > 0)
                    .orElse(BigDecimal.ONE);
            return min.compareTo(max) < 0 ? new ScoringScale(certificateName, min, max, step) : null;
        } catch (Exception ex) {
            log.warn("Invalid scoring config of certificate {}: {}", certificateName, ex.getMessage());
            return null;
        }
    }

    private BigDecimal number(JsonNode node) {
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual() && StringUtils.hasText(node.asText())) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }
}
