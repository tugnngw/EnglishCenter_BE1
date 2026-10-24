package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.Min;
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
 * Admin cập nhật section. Trường null = giữ nguyên.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateExamSectionRequest {

    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    String title;

    /** LISTENING / READING / WRITING / SPEAKING / GENERAL hoặc giá trị tùy cấu hình. */
    @Size(max = 30, message = "Loại section không được vượt quá 30 ký tự")
    String sectionType;

    String instructions;

    String passageText;

    @Size(max = 500, message = "URL audio không được vượt quá 500 ký tự")
    String audioUrl;

    @Min(value = 1, message = "Thứ tự section phải lớn hơn 0")
    Integer sectionOrder;

    @Min(value = 1, message = "Thời lượng section tối thiểu 1 phút")
    Integer durationMinutes;

    UUID certificatePartId;
}
