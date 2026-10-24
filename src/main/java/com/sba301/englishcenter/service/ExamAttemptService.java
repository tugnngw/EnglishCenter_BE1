package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.SaveAnswersRequest;
import com.sba301.englishcenter.dto.request.SubmitAttemptRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Dev 3 - Luồng làm bài của học viên (D3-08 .. D3-10, D3-13, D3-14, D3-16).
 */
public interface ExamAttemptService {

    /**
     * Bắt đầu lượt thi. Nếu học viên đang có lượt in_progress còn hạn của đề này thì trả lại lượt đó
     * (gọi nhiều lần không tạo nhiều lượt).
     */
    AttemptDetailResponse startAttempt(UUID examId);

    /** Trạng thái lượt thi; khi in_progress có kèm câu hỏi (không đáp án) và các câu đã lưu. */
    AttemptDetailResponse getAttempt(UUID attemptId);

    /** Lưu tạm câu trả lời (autosave). */
    AttemptDetailResponse saveAnswers(UUID attemptId, SaveAnswersRequest request);

    /** Nộp bài: khóa lượt thi, chấm tự động và trả kết quả. Nộp lần 2 bị từ chối (409). */
    AttemptResultResponse submitAttempt(UUID attemptId, SubmitAttemptRequest request);

    /** Lịch sử thi của học viên đang đăng nhập (lọc theo đề nếu có examId). */
    PageResponse<AttemptSummaryResponse> getMyAttempts(UUID examId, Pageable pageable);

    AttemptResultResponse getMyAttemptResult(UUID attemptId);

    /** Tự nộp các lượt thi đã quá hạn (job định kỳ). @return số lượt đã đóng. */
    int autoCloseOverdueAttempts();
}
