package com.sba301.englishcenter.dto.response;

import com.sba301.englishcenter.dto.practice.CriterionScoreData;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kết quả AI chấm theo 4 tiêu chí (TASK-D5-04). Không trả phản hồi gốc của AI (rawResponse).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeEvaluationResponse {

    private UUID evaluationId;
    private UUID answerId;
    private UUID practiceExamId;
    private String examTitle;
    private String examType;
    private BigDecimal bandScore;
    private CriterionScoreData taskAchievement;
    private CriterionScoreData coherenceCohesion;
    private CriterionScoreData lexicalResource;
    private CriterionScoreData grammaticalAccuracy;
    private String feedback;
    private List<String> suggestions;
    private String model;
    private Instant evaluatedAt;
}
