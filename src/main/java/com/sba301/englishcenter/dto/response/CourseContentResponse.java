package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
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
public class CourseContentResponse {

    UUID contentId;
    UUID courseId;
    String contentType;
    UUID parentContentId;
    String title;
    String description;
    Integer contentOrder;

    /**
     * Nội dung JSONB content_data đã parse.
     * Với bài học: chứa isPreview, videoUrl, durationMinutes...
     * <p>
     * KHỞI ĐỘNG Ở KIỂU {@link JsonNode} (KHÔNG phải {@code Object}): nếu khai báo
     * là {@code Object}, Jackson sẽ serialize theo kiểu bean → frontend nhận
     * được object meta của JsonNode ({nodeType, containerNode, ...}) thay vì
     * dữ liệu thật. Với {@link JsonNode}, Jackson tự serialize đúng cây JSON.
     */
    JsonNode contentData;

    /**
     * Bài học xem trước được phép hiển thị link video.
     * Bài học không preview mà học viên chưa ghi danh sẽ bị khóa (isLocked = true) và
     * không trả về videoUrl.
     */
    Boolean isPreview;

    /** true nghĩa là học viên chưa đủ quyền xem nội dung này. */
    Boolean isLocked;

    /** Chỉ được trả về khi bài học không bị khóa. */
    String videoUrl;

    /**
     * Id đề thi liên kết (nội dung 'homework' bài trắc nghiệm).
     * <p>
     * Lấy từ FK {@code course_content.exam_id}. Có thể {@code null} nếu bài tập
     * không phải trắc nghiệm, hoặc đề thi đã bị xoá (FK là ON DELETE SET NULL).
     */
    UUID examId;

    /** Tên đề thi liên kết — tiện cho UI hiển thị mà không cần gọi thêm API. */
    String examTitle;

    /**
     * Id kỹ năng (certificate_part) mà bài tập này đo lường.
     * <p>
     * Thêm ở V8. {@code null} = bài tập chưa gắn kỹ năng chứng chỉ.
     */
    UUID certificatePartId;

    /**
     * Danh sách id bộ từ vựng CHUNG được tham chiếu bởi nội dung 'vocabulary'.
     * <p>
     * Chỉ có giá trị khi {@code contentType = 'vocabulary'}; các bộ này đã được
     * gán vào khóa học qua {@code course_vocabulary_sets}.
     */
    List<String> vocabularySetIds;
}