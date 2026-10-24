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
import java.util.List;
import java.util.UUID;

/**
 * Section của đề thi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamSectionResponse {

    UUID sectionId;
    String sectionType;
    String title;
    String instructions;
    String passageText;
    String audioUrl;
    Integer sectionOrder;
    Integer durationMinutes;
    UUID certificatePartId;
    String certificatePartName;
    Integer questionCount;
    BigDecimal maxScore;
    List<ExamQuestionResponse> questions;
}
