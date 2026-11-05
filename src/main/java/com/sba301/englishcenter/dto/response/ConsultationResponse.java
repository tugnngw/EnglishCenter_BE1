package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Yêu cầu tư vấn lộ trình, kèm nguyện vọng và trình độ hiện tại của học viên để mentor tư vấn.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationResponse {

    private UUID requestId;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private String phone;
    private String note;
    private String status;
    private UUID mentorId;
    private String mentorName;
    private UUID targetCertificateId;
    private String targetCertificateName;
    private BigDecimal targetScore;
    private String currentLevel;
    private BigDecimal currentScore;
    private Instant createdAt;
    private Instant lastUpdated;
}
