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
 * Một bộ từ vựng đã được GÁN vào một khóa học.
 * <p>
 * Dùng cho endpoint {@code GET /api/v1/courses/{courseId}/vocabularies} —
 * học viên xem được các bộ đã gán cho khóa học mình đang học.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CourseVocabularySetResponse {

    /** Id bộ trong kho chung. */
    UUID setId;

    /** Id của liên kết (course_vocabulary_sets.id). */
    UUID assignmentId;

    /** Tên bộ. */
    String title;

    /** Mô tả bộ (nếu có). */
    String description;

    /** Thứ tự hiển thị trong khóa học. */
    Integer displayOrder;

    /** Các từ thuộc bộ. */
    List<VocabularyWordResponse> words;

    /** Số từ trong bộ. */
    Integer wordCount;

    /**
     * true = học viên chưa ghi danh khóa học → nội dung bị khoá
     * (không trả về danh sách từ).
     */
    Boolean locked;
}
