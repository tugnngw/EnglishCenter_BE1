package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateHomeworkSubmissionRequest {

    @NotNull(message = "ID bài tập không được để trống")
    UUID homeworkContentId;

    String answerContent;

    @Size(max = 500, message = "URL file không được vượt quá 500 ký tự")
    String fileUrl;

    /* =====================================================
     * Field bổ sung (V8) — nullable, tương thích ngược.
     * ===================================================== */

    /**
     * Hình thức nộp: {@code text} | {@code file} | {@code multiple_choice}.
     * <p>
     * Nếu để trống, BE tự suy ra từ {@code course_content.content_data.submissionType}
     * (fallback {@code text}) — giữ hành vi cũ cho client chưa gửi field này.
     */
    @Size(max = 20, message = "Hình thức nộp không hợp lệ")
    String submissionType;

    /**
     * Lượt thi đã nộp (cho bài trắc nghiệm). BE sẽ chấm lại và tính {@code passed}.
     */
    UUID examAttemptId;

    /** public_id của file đã upload lên Cloudinary (BE đã cấp chữ ký). */
    @Size(max = 255, message = "public_id file không được vượt quá 255 ký tự")
    String filePublicId;

    /** image | raw | video — BE quyết định, client chỉ echo lại giá trị BE cấp. */
    @Size(max = 20, message = "Loại tài nguyên file không hợp lệ")
    String fileResourceType;

    @Size(max = 20, message = "Định dạng file không hợp lệ")
    String fileFormat;

    /** Dung lượng file (byte) — BE kiểm tra lại so với giới hạn đã cấu hình. */
    Long fileBytes;

    @Size(max = 255, message = "Tên file không được vượt quá 255 ký tự")
    String fileOriginalName;
}