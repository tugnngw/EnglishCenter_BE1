package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCourseContentRequest {

    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    String title;

    String description;

    Integer contentOrder;

    UUID parentContentId;

    /**
     * Danh sách id bộ từ vựng CHUNG được tham chiếu bởi nội dung 'vocabulary'.
     * <p>
     * Chỉ dùng khi nội dung có {@code contentType = 'vocabulary'}. Nếu để trống,
     * backend giữ nguyên danh sách đang lưu trong {@code content_data.setIds}.
     */
    List<UUID> vocabularySetIds;

    /**
     * Id đề thi liên kết cho nội dung 'homework' (bài trắc nghiệm).
     * <p>
     * Chỉ dùng khi nội dung có {@code contentType = 'homework'}. Nếu để trống,
     * backend giữ nguyên đề thi đang liên kết.
     */
    UUID examId;

    /**
     * Kỹ năng (certificate_part) mà bài tập này đo lường.
     * <p>
     * Chỉ dùng khi nội dung có {@code contentType = 'homework'}. Nếu để trống,
     * backend giữ nguyên kỹ năng đang liên kết.
     */
    UUID certificatePartId;

    String contentData;
}