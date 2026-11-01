package com.sba301.englishcenter.dto.practice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Kết quả AI chấm một câu Writing / Speaking của đề luyện tập (cột exam_attempt_details.ai_evaluation).
 * bandScore theo thang chứng chỉ mục tiêu [minScore, maxScore]; điểm câu = points * (band - min) / (max - min).
 *
 * @param skill WRITING | SPEAKING
 */
public record EssayAiEvaluationData(String skill,
                                    BigDecimal bandScore,
                                    String cefrLevel,
                                    String certificateName,
                                    BigDecimal minScore,
                                    BigDecimal maxScore,
                                    CriterionScoreData taskAchievement,
                                    CriterionScoreData coherenceCohesion,
                                    CriterionScoreData lexicalResource,
                                    CriterionScoreData grammaticalAccuracy,
                                    String feedback,
                                    List<String> suggestions,
                                    String model,
                                    LocalDateTime evaluatedAt) {
}
