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
 * Một BỘ từ vựng trong KHO TỪ VỰNG CHUNG.
 * <p>
 * Đây là đơn vị Admin/Mentor tick chọn khi gán vào khóa học.
 * Bộ không mang {@code courseId} — nó thuộc kho chung, độc lập với khóa học.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabularySetResponse {

    /** Id bộ trong kho chung. */
    UUID setId;

    /** Tên bộ. */
    String title;

    /** Mô tả bộ (nếu có). */
    String description;

    /** Các từ thuộc bộ. */
    List<VocabularyWordResponse> words;

    /** Số từ trong bộ. */
    Integer wordCount;

    /** Người tạo bộ trong kho chung. */
    UUID createdByUserId;

    String createdByUserName;

    /**
     * Số khóa học đang dùng bộ này — Admin biết bộ "đã được dùng ở đâu".
     */
    Integer usedInCourseCount;
}
