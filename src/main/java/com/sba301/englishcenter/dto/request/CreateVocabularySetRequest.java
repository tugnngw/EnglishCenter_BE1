package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

/**
 * Tạo một BỘ từ vựng mới trong KHO TỪ VỰNG CHUNG.
 * <p>
 * Không có {@code courseId} — bộ từ vựng được tạo độc lập, không phụ thuộc
 * vào khóa học nào lúc tạo. Việc gán vào khóa học thực hiện ở endpoint
 * {@code PUT /api/v1/vocabulary-sets/courses/{courseId}}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateVocabularySetRequest {

    @NotBlank(message = "Tiêu đề bộ từ vựng không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    String title;

    String description;

    /**
     * Danh sách từ thuộc bộ.
     * <p>
     * Mỗi từ được gửi dưới dạng object:
     * <pre>{ "word": "academic", "ipa": "/…/", "meaning": "Học thuật" }</pre>
     * Backend sẽ TÁI SỬ DỤNG từ đã có trong kho chung (không nhân bản).
     */
    @Builder.Default
    List<VocabularyWordRequest> words = new ArrayList<>();
}
