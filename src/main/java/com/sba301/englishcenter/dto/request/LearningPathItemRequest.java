package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.LearningPathItemType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 1 chặng trong lộ trình (TASK-D5-03). refId trỏ tới khóa học / đề thi / đề luyện tập tùy type.
 */
@Getter
@Setter
public class LearningPathItemRequest {

    @NotBlank(message = "Tên giai đoạn không được để trống")
    @Size(max = 100, message = "Tên giai đoạn không được vượt quá 100 ký tự")
    private String stage;

    @NotNull(message = "Thứ tự không được để trống")
    @Min(value = 1, message = "Thứ tự phải từ 1")
    private Integer order;

    @NotNull(message = "Loại chặng không được để trống (COURSE / EXAM / PRACTICE_EXAM)")
    private LearningPathItemType type;

    @NotNull(message = "refId không được để trống")
    private UUID refId;

    // Chỉ dùng cho PRACTICE_EXAM: band AI chấm cần đạt (bỏ trống = chỉ cần có bài đã chấm)
    @DecimalMin(value = "0.0", message = "Band mục tiêu từ 0 đến 9")
    @DecimalMax(value = "9.0", message = "Band mục tiêu từ 0 đến 9")
    private BigDecimal targetBand;
}
