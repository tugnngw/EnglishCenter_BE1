package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.dto.request.PracticeExamRequest;
import com.sba301.englishcenter.dto.request.SubmitPracticeAnswerRequest;
import com.sba301.englishcenter.dto.response.PracticeAnswerResponse;
import com.sba301.englishcenter.dto.response.PracticeEvaluationResponse;
import com.sba301.englishcenter.dto.response.PracticeExamResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Kho đề luyện tập tự do + AI chấm bài Writing / Speaking (TASK-D5-04).
 */
public interface PracticeExamService {

    PageResponse<PracticeExamResponse> getPracticeExams(PracticeExamType type, String keyword, Pageable pageable);

    PageResponse<PracticeExamResponse> getPracticeExams(PracticeExamType type, String keyword, UUID certificateId, Pageable pageable);

    PracticeExamResponse getPracticeExam(UUID practiceExamId);

    PracticeExamResponse create(UUID adminId, PracticeExamRequest request);

    PracticeExamResponse update(UUID practiceExamId, PracticeExamRequest request);

    /** Không xóa được đề đã có bài làm. */
    void delete(UUID practiceExamId);

    /** Học viên nộp bài (được nộp nhiều lần, mỗi lần là 1 bài làm mới). */
    PracticeAnswerResponse submit(UUID studentId, UUID practiceExamId, SubmitPracticeAnswerRequest request);

    /** Gọi AI chấm bài làm của chính mình; bài đã chấm thì trả lại kết quả cũ (không gọi AI lần 2). */
    PracticeEvaluationResponse evaluate(UUID studentId, UUID answerId);

    /** Xem kết quả chấm: chủ bài làm hoặc ADMIN. */
    PracticeEvaluationResponse getEvaluation(UUID requesterId, boolean isAdmin, UUID evaluationId);

    /** Bài làm của học viên, mới nhất trước. */
    List<PracticeAnswerResponse> getMyAnswers(UUID studentId);

    /** Tiêu đề đề luyện tập (Learning Path dùng khi admin thêm chặng). */
    String getTitle(UUID practiceExamId);

    /** Học viên đã có bài được AI chấm đạt band mục tiêu chưa (targetBand null = chỉ cần có bài đã chấm). */
    boolean hasReachedBand(UUID studentId, UUID practiceExamId, BigDecimal targetBand);
}
