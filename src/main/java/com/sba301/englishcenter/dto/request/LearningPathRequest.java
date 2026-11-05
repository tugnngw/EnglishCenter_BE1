package com.sba301.englishcenter.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Admin / Mentor tạo, sửa lộ trình học (TASK-D5-03).
 * totalSessions: số buổi kèm 1-1 của lộ trình (VD 12) - gói kèm 1-1 theo lộ trình phải đúng số buổi này.
 */
@Getter
@Setter
public class LearningPathRequest {

    @NotNull(message = "Chứng chỉ không được để trống")
    private UUID certificateId;

    @NotBlank(message = "Tên lộ trình không được để trống")
    @Size(max = 255, message = "Tên lộ trình không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả không được vượt quá 5000 ký tự")
    private String description;

    @Min(value = 1, message = "Số buổi kèm 1-1 tối thiểu là 1")
    @Max(value = 100, message = "Số buổi kèm 1-1 tối đa là 100")
    private Integer totalSessions;

    @NotEmpty(message = "Lộ trình phải có ít nhất 1 chặng")
    @Size(max = 100, message = "Lộ trình tối đa 100 chặng")
    private List<@Valid @NotNull LearningPathItemRequest> items;
}
