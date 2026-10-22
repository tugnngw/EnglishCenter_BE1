package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateCourseRequest {

    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    String title;

    String description;

    @Size(max = 100, message = "Level không được vượt quá 100 ký tự")
    String level;

    @DecimalMin(value = "0.0", message = "Giá khóa học phải lớn hơn hoặc bằng 0")
    BigDecimal price;

    @PositiveOrZero(message = "Điểm mục tiêu phải lớn hơn hoặc bằng 0")
    BigDecimal targetScore;

    @Size(max = 500, message = "URL ảnh không được vượt quá 500 ký tự")
    String thumbnailUrl;

    UUID certificateId;

    UUID certificatePartId;

    UUID mentorId;

    /** draft / active / archived */
    String status;
}