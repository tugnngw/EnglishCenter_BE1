package com.sba301.englishcenter.dto.practice;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Kết quả AI chấm, lưu trong bài làm (practice_exams.answers JSONB) — TASK-D5-04.
 * rawResponse: phản hồi gốc của AI để đối soát, không trả ra API.
 * certificateName / minScore / maxScore: thang chứng chỉ đã dùng để chấm; cefrLevel: level CEFR tương ứng.
 * Bài chấm trước khi có thang theo chứng chỉ: các trường này null (FE hiểu là IELTS 0 – 9).
 */
public record PracticeEvaluationData(UUID evaluationId, BigDecimal bandScore,
                                     CriterionScoreData taskAchievement, CriterionScoreData coherenceCohesion,
                                     CriterionScoreData lexicalResource, CriterionScoreData grammaticalAccuracy,
                                     String feedback, List<String> suggestions, String model, String rawResponse,
                                     LocalDateTime evaluatedAt,
                                     String certificateName, BigDecimal minScore, BigDecimal maxScore,
                                     String cefrLevel) {
}
