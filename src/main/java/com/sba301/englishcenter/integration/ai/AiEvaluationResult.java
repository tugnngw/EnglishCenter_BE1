package com.sba301.englishcenter.integration.ai;

import com.sba301.englishcenter.dto.practice.CriterionScoreData;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả chấm đã chuẩn hóa từ AI (band 0 - 9, bước 0.5) — TASK-D5-04.
 */
public record AiEvaluationResult(BigDecimal bandScore,
                                 CriterionScoreData taskAchievement, CriterionScoreData coherenceCohesion,
                                 CriterionScoreData lexicalResource, CriterionScoreData grammaticalAccuracy,
                                 String feedback, List<String> suggestions, String model, String rawResponse) {
}
