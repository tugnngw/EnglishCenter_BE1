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
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MentorResponse {
    UUID userId;
    String email;
    String fullName;
    String phone;
    Boolean isActive;
    UUID certificateId;
    String certificateName;
    BigDecimal certificateScore;
    String scoreUnit;
    String specialization;
    String department;
    String avatarUrl;
    String biography;
    BigDecimal hourlyRate;
    BigDecimal ratingAverage;
    LocalDateTime mentorRegisteredAt;
    LocalDateTime createdAt;
    LocalDateTime lastUpdated;
}
