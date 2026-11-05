package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    private UUID targetCertificateId;
    private String targetCertificateName;
    private BigDecimal currentScore;
    private String currentLevel;
    private BigDecimal targetScore;
    private List<CourseResponse> courses;
    private List<LearningPathResponse> learningPaths;
    private String summary;
    // true = không có khóa vừa sức với trình độ hiện tại -> FE mời để lại SĐT nhờ mentor tư vấn
    private boolean needConsultation;
}
