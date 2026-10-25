package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

/**
 * Một mục trong "thư viện từ vựng" — đơn vị Admin có thể chọn từ để gắn vào
 * một nội dung vocabulary của khóa học.
 * <p>
 * Backend không có bảng "vocabulary library" riêng: mọi từ vựng đều là
 * {@code course_content} có {@code content_type = 'vocabulary'}, với 2 shape:
 * <ul>
 *   <li>shape BỘ   → {@code content_data.items} (nhiều từ trong 1 row) → {@code kind = 'set'}</li>
 *   <li>shape ĐƠN   → {@code content_data.word} (1 flashcard)          → {@code kind = 'word'}</li>
 * </ul>
 * Mỗi mục luôn mang {@code sourceContentId} = id THẬT của row {@code course_content}
 * để frontend dùng làm khoá truy vết nguồn khi sao chép từ vào bộ mới.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabularyLibraryItemResponse {

    /** Id thật của row {@code course_content} chứa từ vựng. */
    UUID sourceContentId;

    /** Khóa học sở hữu từ vựng (null = từ vựng dùng chung). */
    UUID courseId;

    /** Tên khóa học sở hữu (để Admin phân biệt nguồn từ). */
    String courseTitle;

    /** Tên bộ / tiêu đề row nguồn. */
    String setTitle;

    /** {@code set} = bộ nhiều từ · {@code word} = 1 flashcard đơn. */
    String kind;

    /** Các từ thuộc mục này. */
    List<VocabularyWordResponse> words;

    /** Số từ trong mục. */
    Integer wordCount;
}
