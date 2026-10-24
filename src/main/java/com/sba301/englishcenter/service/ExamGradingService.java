package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.GradeAnswerRequest;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Dev 3 - Chấm tay Writing/Speaking (D3-12).
 * Admin chấm được mọi lượt thi; mentor chỉ chấm đề thuộc khóa học mình phụ trách.
 */
public interface ExamGradingService {

    /** Hàng đợi lượt thi còn câu essay chưa chấm (cũ nhất trước). */
    PageResponse<AttemptSummaryResponse> getPendingAttempts(Pageable pageable);

    /** Toàn bộ bài làm của một lượt thi (luôn kèm đáp án, bài viết, điểm). */
    AttemptResultResponse getAttemptForGrading(UUID attemptId);

    /** Chấm một câu essay; khi hết câu chờ chấm, lượt thi submitted chuyển sang graded. */
    AttemptResultResponse gradeAnswer(UUID attemptId, UUID questionId, GradeAnswerRequest request);
}
