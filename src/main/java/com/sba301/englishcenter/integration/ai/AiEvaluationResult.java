package com.sba301.englishcenter.integration.ai;

import com.sba301.englishcenter.dto.practice.CriterionScoreData;
import com.sba301.englishcenter.dto.practice.PronunciationIssueData;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả chấm đã chuẩn hóa từ AI theo thang của chứng chỉ ({@link ScoringScale}) — TASK-D5-04.
 * bandScore: điểm tổng theo thang chứng chỉ; cefrLevel: level CEFR tương ứng (A1 – C2).
 * weaknesses: các điểm yếu cụ thể; pronunciationIssues: từ nghi phát âm sai (chỉ Speaking).
 */
public record AiEvaluationResult(BigDecimal bandScore,
                                 CriterionScoreData taskAchievement, CriterionScoreData coherenceCohesion,
                                 CriterionScoreData lexicalResource, CriterionScoreData grammaticalAccuracy,
                                 String feedback, List<String> suggestions, String model, String rawResponse,
                                 String cefrLevel, List<String> weaknesses,
                                 List<PronunciationIssueData> pronunciationIssues) {

    /** Giữ constructor cũ (chưa có weaknesses / pronunciationIssues). */
    public AiEvaluationResult(BigDecimal bandScore,
                              CriterionScoreData taskAchievement, CriterionScoreData coherenceCohesion,
                              CriterionScoreData lexicalResource, CriterionScoreData grammaticalAccuracy,
                              String feedback, List<String> suggestions, String model, String rawResponse,
                              String cefrLevel) {
        this(bandScore, taskAchievement, coherenceCohesion, lexicalResource, grammaticalAccuracy, feedback,
                suggestions, model, rawResponse, cefrLevel, List.of(), List.of());
    }
}
