package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mentor tạo / sửa bài tập riêng (V27). Lưu vào course_content (course_id = NULL), các field cấu hình nằm trong
 * content_data giống homework khóa học của Dev 2 để dùng lại luồng nộp bài / chấm bài.
 * Thường điền sẵn từ "Import PDF" (AI đọc đề), mentor chỉ chỉnh hạn nộp / điểm.
 */
@Getter
@Setter
public class MentorHomeworkRequest {

    @NotBlank(message = "Tên bài tập không được để trống")
    @Size(max = 255, message = "Tên bài tập không được vượt quá 255 ký tự")
    private String title;

    // Đề bài dạng chữ (AI đọc từ PDF) - AI chấm bài dựa vào phần này
    @Size(max = 20000, message = "Đề bài không được vượt quá 20000 ký tự")
    private String instructions;

    // Hình thức nộp: text (gõ bài) / file (nộp file) / audio (ghi âm). Không hỗ trợ trắc nghiệm
    @NotBlank(message = "Hình thức nộp không được để trống")
    @Pattern(regexp = "text|file|audio", message = "Hình thức nộp chỉ gồm text, file, audio")
    private String submissionType;

    @NotNull(message = "Điểm tối đa không được để trống")
    @DecimalMin(value = "1", message = "Điểm tối đa từ 1 đến 100")
    @DecimalMax(value = "100", message = "Điểm tối đa từ 1 đến 100")
    private BigDecimal maxScore;

    // % điểm cần đạt (bỏ trống = 50%)
    @DecimalMin(value = "0", message = "Điểm đạt từ 0 đến 100%")
    @DecimalMax(value = "100", message = "Điểm đạt từ 0 đến 100%")
    private BigDecimal passingScore;

    // Số lần nộp tối đa (bỏ trống = không giới hạn)
    @Min(value = 1, message = "Số lần nộp tối đa từ 1 đến 10")
    @Max(value = 10, message = "Số lần nộp tối đa từ 1 đến 10")
    private Integer maxAttempts;

    // Hạn nộp: số ngày kể từ khi học viên ghi danh lộ trình chứa bài (bỏ trống = không hạn)
    @Min(value = 1, message = "Hạn nộp từ 1 đến 365 ngày")
    @Max(value = 365, message = "Hạn nộp từ 1 đến 365 ngày")
    private Integer dueDays;

    // Đuôi file được nộp khi submissionType = file (bỏ trống = pdf)
    @Size(max = 10, message = "Tối đa 10 định dạng file")
    private List<@Pattern(regexp = "[a-zA-Z0-9]{1,10}", message = "Định dạng file không hợp lệ") String> allowedFileTypes;

    // File PDF đề gốc (upload Cloudinary purpose = attachment) để học viên xem
    @Size(max = 1000, message = "Link tài liệu quá dài")
    private String attachmentUrl;

    @Size(max = 255, message = "Tên tài liệu quá dài")
    private String attachmentName;

    @Size(max = 255, message = "Public ID tài liệu quá dài")
    private String attachmentPublicId;
}
