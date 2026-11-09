package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Mentor import đề bài tập từ PDF (V27): FE gửi PDF dạng base64 (giống import đề của admin),
 * Claude đọc và trả bản nháp để mentor xem / sửa rồi mới lưu.
 */
@Getter
@Setter
public class HomeworkAiImportRequest {

    @NotBlank(message = "Chưa chọn file PDF")
    private String fileBase64;

    @Size(max = 255, message = "Tên file quá dài")
    private String fileName;
}
