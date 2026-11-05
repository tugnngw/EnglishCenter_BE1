package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.dto.request.SaveAnswersRequest;
import com.sba301.englishcenter.dto.request.SubmitAttemptRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
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

    /** Lịch sử thi lọc theo đề hoặc phạm vi đề (VD practice cho trang Luyện tập AI). */
    PageResponse<AttemptSummaryResponse> getMyAttempts(UUID examId, ExamScope examScope, Pageable pageable);

    /**
     * Đề luyện tập: AI (Claude) chấm các câu Writing / Speaking chưa có điểm theo thang chứng chỉ mục tiêu,
     * rồi tính lại tổng điểm. Reading / Listening đã tự chấm theo đáp án lúc nộp. Gọi lại khi đã chấm hết
     * thì trả kết quả hiện tại.
     */
    AttemptResultResponse aiGradeAttempt(UUID attemptId);

    AttemptResultResponse getMyAttemptResult(UUID attemptId);

    /** Học viên đã thi đạt đề này chưa (Dev 5 dùng để tính tiến độ Learning Path). */
    boolean hasPassedExam(UUID studentId, UUID examId);

    /**
     * Đề luyện tập AI (examScope = practice): học viên đã có lượt chấm xong đạt band mục tiêu chưa
     * (band theo thang chứng chỉ mục tiêu, giống trang kết quả). targetBand = null: chỉ cần 1 lượt chấm xong.
     * Dev 5 dùng để tính tiến độ chặng PRACTICE_EXAM của Learning Path.
     */
    boolean hasReachedPracticeBand(UUID studentId, UUID examId, BigDecimal targetBand);

    /** Tự nộp các lượt thi đã quá hạn (job định kỳ). @return số lượt đã đóng. */
    int autoCloseOverdueAttempts();
}
