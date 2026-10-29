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

import java.util.UUID;

/**
 * Xin chữ ký upload Cloudinary.
 * <p>
 * BE quyết định {@code resourceType} và {@code folder} theo nghiệp vụ bài tập;
 * client chỉ nêu mục đích + định dạng file để BE validate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UploadSignatureRequest {

    /**
     * Mục đích upload — BE dùng để suy ra resource_type + folder:
     * {@code homework_attachment} | {@code homework_submission} | {@code submission_audio}.
     */
    @NotBlank(message = "Mục đích upload không được để trống")
    @Size(max = 40, message = "Mục đích upload không hợp lệ")
    String purpose;

    /** Bài tập liên quan (dùng làm folder + kiểm tra quyền truy cập). */
    UUID homeworkContentId;

    /** Định dạng file (pdf, docx, mp3, mp4...) — BE kiểm tra theo quy tắc. */
    @Size(max = 20, message = "Định dạng file không hợp lệ")
    String fileFormat;

    /** Dung lượng (byte) — BE chặn nếu vượt giới hạn. */
    Long fileBytes;

    /** Tên file gốc (để lưu metadata + kiểm tra đuôi file). */
    @Size(max = 255, message = "Tên file không được vượt quá 255 ký tự")
    String fileName;
}
