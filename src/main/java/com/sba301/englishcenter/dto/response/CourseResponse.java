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
public class CourseResponse {

    UUID courseId;
    String code;
    String title;
    String description;
    String level;
    BigDecimal price;
    BigDecimal targetScore;
    String thumbnailUrl;
    String status;

    UUID certificateId;
    String certificateName;
    UUID certificatePartId;
    String certificatePartName;

    /** Null nghĩa là khóa học không gắn mentor (khóa học VOD tự học độc lập). */
    UUID mentorId;
    String mentorName;

    UUID createdByUserId;
    LocalDateTime createdAt;
    LocalDateTime lastUpdated;
}