package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Điểm theo section.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SectionScoreResponse {

    UUID sectionId;
    /** Phần thi của chứng chỉ (Dev 2) mà section này thuộc về; FE lấy thang điểm của phần thi từ đây */
    UUID certificatePartId;
    String sectionType;
    String title;
    Integer questionCount;
    Integer correctCount;
    BigDecimal earnedScore;
    BigDecimal maxScore;
    BigDecimal percentage;
    Boolean pendingManualGrading;
}
