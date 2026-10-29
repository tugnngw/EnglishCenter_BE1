package com.sba301.englishcenter.integration.ai;

import com.sba301.englishcenter.common.enums.PracticeExamType;

/**
 * Cổng gọi AI chấm bài Writing / Speaking (TASK-D5-04). Dev 3 có thể tái sử dụng cho câu ESSAY.
 * Ném AppException(AI_NOT_CONFIGURED) khi thiếu cấu hình, AppException(AI_EVALUATION_FAILED) khi AI lỗi.
 */
public interface AiEvaluationClient {

    /**
     * @param type     kỹ năng (WRITING / SPEAKING)
     * @param question đề bài
     * @param answer   bài viết, hoặc văn bản phiên âm bài nói
     * @param scale    thang điểm theo chứng chỉ mục tiêu của người học
     */
    AiEvaluationResult evaluate(PracticeExamType type, String question, String answer, ScoringScale scale);
}
