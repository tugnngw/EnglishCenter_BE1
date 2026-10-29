package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID userId;
    private String email;
    private String fullName;
    private String phone;
    private String role;
    private Boolean isActive;
    private LocalDateTime createdAt;

    // Hồ sơ người học (USER / STUDENT); null với MENTOR / ADMIN hoặc khi chưa có
    private UUID targetCertificateId;
    private BigDecimal targetScore;
    private String currentLevel;
    private BigDecimal currentScore;
}
