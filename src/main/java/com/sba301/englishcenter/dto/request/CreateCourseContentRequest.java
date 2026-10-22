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
     * JSON string lưu vào cột content_data (JSONB).
     * Ví dụ lesson: {"isPreview":true,"videoUrl":"...","durationMinutes":30}
     * Ví dụ vocabulary: {"word":"academic","phonetic":"...","audioUrl":"...","meaning":"..."}
     */
    String contentData;
}