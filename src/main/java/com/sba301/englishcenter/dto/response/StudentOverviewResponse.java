package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thông tin học viên cho mentor tìm kiếm / tư vấn: liên hệ, nguyện vọng, trình độ test level.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentOverviewResponse {

    private UUID userId;
    private String fullName;
    private String email;
    private String phone;
    /** USER (Learner) | STUDENT */
    private String role;
    private UUID targetCertificateId;
    private String targetCertificateName;
    private BigDecimal targetScore;
    private String currentLevel;
    private BigDecimal currentScore;
    private long levelTestCount;
    /** Trạng thái yêu cầu tư vấn gần nhất, null = chưa gửi */
    private String consultationStatus;
}
