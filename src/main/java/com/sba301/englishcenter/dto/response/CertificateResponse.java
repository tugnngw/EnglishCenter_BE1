package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CertificateResponse {

    UUID certificateId;
    String certificateName;
    String description;
    BigDecimal minScore;
    BigDecimal maxScore;
    BigDecimal scoreStep;
    String scoreUnit;
    String scaleType;
    String roundingRule;
    String standardScale;
    Integer partCount;
    List<CertificatePartResponse> parts;
    LocalDateTime createdAt;
    LocalDateTime lastUpdated;
}