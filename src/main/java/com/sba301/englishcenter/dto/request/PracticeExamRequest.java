package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.PracticeExamType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Admin tạo / sửa đề luyện tập Writing / Speaking (TASK-D5-04).
 */
@Getter
@Setter
public class PracticeExamRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    // Đề bài hiển thị cho học viên và gửi cho AI chấm
    @NotBlank(message = "Đề bài không được để trống")
    @Size(max = 5000, message = "Đề bài không được vượt quá 5000 ký tự")
    private String description;

    @NotNull(message = "Kỹ năng không được để trống (WRITING / SPEAKING)")
    private PracticeExamType examType;

    @Size(max = 255, message = "Tên bộ đề không được vượt quá 255 ký tự")
    private String bankName;

    // Khóa học liên quan (không bắt buộc)
    private UUID courseId;
}
