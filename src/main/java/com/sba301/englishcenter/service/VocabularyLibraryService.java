package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.VocabularyLibraryItemResponse;

import java.util.List;

/**
 * Thư viện từ vựng TOÀN HỆ THỐNG.
 * <p>
 * Backend KHÔNG có bảng "vocabulary library" riêng — mọi từ vựng đều là
 * {@code course_content} có {@code content_type = 'vocabulary'}. Vì vậy
 * "thư viện" chính là danh sách TẤT CẢ các nội dung vocabulary của mọi khóa học,
 * đã parse sẵn danh sách từ bên trong {@code content_data}.
 * <p>
 * Admin dùng endpoint này để chọn từ đã tạo sẵn (kể cả từ khóa học khác)
 * và gắn vào một nội dung vocabulary mới.
 */
public interface VocabularyLibraryService {

    /**
     * Toàn bộ mục từ vựng của mọi khóa học.
     * <p>
     * Mỗi mục mang {@code sourceContentId} = id THẬT của row
     * {@code course_content} để frontend truy vết nguồn khi sao chép từ.
     * Các row không đọc được từ nào (JSONB rỗng / hỏng) sẽ bị bỏ qua.
     *
     * @return danh sách mục thư viện (không null, có thể rỗng)
     */
    List<VocabularyLibraryItemResponse> getLibrary();
}
