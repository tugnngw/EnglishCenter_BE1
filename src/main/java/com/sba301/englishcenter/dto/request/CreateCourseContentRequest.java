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

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateCourseContentRequest {

    /** lesson / vocabulary / homework */
    @NotBlank(message = "Loại nội dung không được để trống")
    String contentType;

    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    String title;

    String description;

    Integer contentOrder;

    UUID parentContentId;

    /**
     * Danh sách id bộ từ vựng CHUNG được tham chiếu bởi nội dung 'vocabulary'.
     * <p>
     * Chỉ dùng khi {@code contentType = 'vocabulary'}. Nội dung này KHÔNG chứa
     * từ — nó là con trỏ tới các bộ trong kho từ vựng chung; các bộ sẽ được gán
     * vào khóa học qua bảng {@code course_vocabulary_sets}.
     * <p>
     * Nếu để trống, backend sẽ đọc {@code content_data.setIds}.
     */
    List<UUID> vocabularySetIds;

    /**
     * Id đề thi liên kết cho nội dung 'homework' (bài trắc nghiệm).
     * <p>
     * Chỉ dùng khi {@code contentType = 'homework'}. Backend sẽ set FK
     * {@code course_content.exam_id} và ghi lại {@code examId} vào
     * {@code content_data}. Nếu để trống, backend đọc {@code content_data.examId}.
     */
    UUID examId;

    /**
     * Kỹ năng (certificate_part) mà bài tập này đo lường.
     * <p>
     * Chỉ dùng khi {@code contentType = 'homework'}. Cho phép null (bài tập
     * chưa gắn kỹ năng). Backend set FK {@code course_content.certificate_part_id}.
     */
    UUID certificatePartId;

    /**
     * JSON string lưu vào cột content_data (JSONB).
     * Ví dụ lesson: {"isPreview":true,"videoUrl":"...","durationMinutes":30}
     * Ví dụ vocabulary: {"setIds":["...uuid..."]}
     * Ví dụ homework: {"submissionType":"multiple_choice","examId":"...uuid..."}
     */
    String contentData;
}