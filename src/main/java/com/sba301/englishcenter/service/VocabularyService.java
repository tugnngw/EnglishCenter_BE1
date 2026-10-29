package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.AssignVocabularySetsRequest;
import com.sba301.englishcenter.dto.request.CreateVocabularySetRequest;
import com.sba301.englishcenter.dto.response.CourseVocabularySetResponse;
import com.sba301.englishcenter.dto.response.VocabularySetResponse;

import java.util.List;
import java.util.UUID;

/**
 * Quản lý TỪ VỰNG theo mô hình KHO CHUNG + GÁN VÀO KHÓA HỌC.
 * <p>
 * <b>Kho từ vựng chung ({@code vocabulary_sets} / {@code vocabulary_words})</b>:
 * từ vựng được tạo độc lập, KHÔNG gắn với khóa học nào lúc tạo.
 * <p>
 * <b>Gán vào khóa học ({@code course_vocabulary_sets})</b>: Admin/Mentor duyệt
 * kho chung trong phần "Quản lý nội dung khóa học", tick chọn các bộ phù hợp
 * rồi lưu — đây là quan hệ Many-to-Many giữa {@code Course} và
 * {@code VocabularySet}.
 */
public interface VocabularyService {

    /**
     * Toàn bộ bộ trong KHO TỪ VỰNG CHUNG (không phụ thuộc khóa học).
     *
     * @param keyword lọc theo tên bộ hoặc theo từ trong bộ (không bắt buộc)
     */
    List<VocabularySetResponse> getBankSets(String keyword);

    /** Chi tiết 1 bộ trong kho chung. */
    VocabularySetResponse getBankSet(UUID setId);

    /** Tạo bộ mới trong kho chung (từ được tái sử dụng nếu đã tồn tại). */
    VocabularySetResponse createBankSet(CreateVocabularySetRequest request);

    /** Cập nhật bộ trong kho chung (tiêu đề / mô tả / danh sách từ). */
    VocabularySetResponse updateBankSet(UUID setId, CreateVocabularySetRequest request);

    /** Xoá bộ khỏi kho chung (kèm gỡ khỏi mọi khóa học đang dùng). */
    void deleteBankSet(UUID setId);

    /** Các bộ đã được gán cho 1 khóa học. */
    List<CourseVocabularySetResponse> getCourseSets(UUID courseId);

    /**
     * Gán danh sách bộ chung vào khóa học (thay thế toàn bộ danh sách cũ).
     * Bộ nào không có trong request sẽ bị gỡ khỏi khóa học.
     */
    List<CourseVocabularySetResponse> assignSets(UUID courseId, AssignVocabularySetsRequest request);

    /** Gỡ 1 bộ khỏi khóa học (không xoá bộ khỏi kho chung). */
    void unassignSet(UUID courseId, UUID setId);
}
