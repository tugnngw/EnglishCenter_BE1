package com.sba301.englishcenter.dto.practice;

import com.sba301.englishcenter.common.enums.PracticeAnswerStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 1 bài làm của học viên, lưu trong mảng practice_exams.answers (JSONB) — TASK-D5-04.
 * evaluation = null khi chưa chấm. Thời điểm lưu UTC (D5-19).
 */
public record PracticeAnswerData(UUID answerId, UUID studentId, String content, int wordCount,
                                 PracticeAnswerStatus status, LocalDateTime submittedAt,
                                 PracticeEvaluationData evaluation) {

    public PracticeAnswerData withEvaluation(PracticeEvaluationData result) {
        return new PracticeAnswerData(answerId, studentId, content, wordCount, PracticeAnswerStatus.EVALUATED,
                submittedAt, result);
    }
}
