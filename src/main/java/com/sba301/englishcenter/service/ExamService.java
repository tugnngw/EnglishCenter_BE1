package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.dto.request.CreateExamRequest;
import com.sba301.englishcenter.dto.request.UpdateExamRequest;
import com.sba301.englishcenter.dto.response.ExamDetailResponse;
import com.sba301.englishcenter.dto.response.ExamSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Dev 3 - Quản lý đề thi (D3-02, D3-06, D3-07).
 */
public interface ExamService {

    /** Danh sách đề công khai - luôn chỉ trả đề published. */
    PageResponse<ExamSummaryResponse> getPublishedExams(Pageable pageable, String keyword, ExamType examType,
                                                        ExamScope examScope, UUID certificateId, UUID courseId,
                                                        String level, DifficultyLevel difficulty, Boolean isFree);

    /** Danh sách đề cho admin - mọi trạng thái, lọc được theo status. */
    PageResponse<ExamSummaryResponse> getExamsForAdmin(Pageable pageable, String keyword, ExamType examType,
                                                       ExamStatus status, ExamScope examScope, UUID certificateId,
                                                       UUID courseId, String level, DifficultyLevel difficulty,
                                                       Boolean isFree);

    /** Chi tiết đề theo quyền của người xem (admin thấy đáp án, người khác chỉ thấy cấu trúc). */
    ExamDetailResponse getExamDetail(UUID examId);

    ExamDetailResponse createExam(CreateExamRequest request);

    ExamDetailResponse updateExam(UUID examId, UpdateExamRequest request);

    void deleteExam(UUID examId);

    ExamDetailResponse publishExam(UUID examId);

    ExamDetailResponse unpublishExam(UUID examId);
}
