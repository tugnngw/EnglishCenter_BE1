package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.dto.request.CreateExamQuestionRequest;
import com.sba301.englishcenter.dto.request.CreateExamSectionRequest;
import com.sba301.englishcenter.dto.request.UpdateExamQuestionRequest;
import com.sba301.englishcenter.dto.request.UpdateExamSectionRequest;
import com.sba301.englishcenter.dto.response.ExamQuestionResponse;
import com.sba301.englishcenter.dto.response.ExamSectionResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Dev 3 - Quản lý cấu trúc đề: section (D3-03), câu hỏi (D3-04), đáp án (D3-05), ngân hàng câu hỏi.
 * Mọi thao tác ghi chỉ được phép khi đề ở trạng thái draft và chưa có lượt thi.
 */
public interface ExamStructureService {

    ExamSectionResponse createSection(UUID examId, CreateExamSectionRequest request);

    ExamSectionResponse updateSection(UUID examId, UUID sectionId, UpdateExamSectionRequest request);

    /** Xóa section kéo theo gỡ các câu hỏi của section khỏi đề (câu hỏi vẫn còn trong ngân hàng). */
    void deleteSection(UUID examId, UUID sectionId);

    ExamQuestionResponse addQuestion(UUID examId, CreateExamQuestionRequest request);

    ExamQuestionResponse updateQuestion(UUID examId, UUID questionId, UpdateExamQuestionRequest request);

    /** Gỡ câu hỏi khỏi đề (không xóa khỏi ngân hàng câu hỏi). */
    void removeQuestion(UUID examId, UUID questionId);

    PageResponse<ExamQuestionResponse> searchQuestionBank(Pageable pageable, String keyword, QuestionType type,
                                                          DifficultyLevel difficulty, UUID certificateId,
                                                          UUID certificatePartId, String topic);

    ExamQuestionResponse getBankQuestion(UUID questionId);
}
