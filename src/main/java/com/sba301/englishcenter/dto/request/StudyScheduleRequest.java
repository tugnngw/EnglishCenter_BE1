package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.RepeatRule;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Học viên tạo / sửa lịch học cá nhân (TASK-D5-01). Thời điểm gửi dạng ISO-8601 UTC (D5-19).
 */
@Getter
@Setter
public class StudyScheduleRequest {

    @NotBlank(message = "Tiêu đề không được để trống")
    @Size(max = 255, message = "Tiêu đề không được vượt quá 255 ký tự")
    private String title;

    @NotNull(message = "Thời gian học không được để trống")
    private Instant scheduledTime;

    // Mặc định không lặp lại
    private RepeatRule repeatRule;

    // Nhắc trước bao nhiêu phút (0 - 1440, mặc định 15 theo V1)
    @Min(value = 0, message = "Số phút nhắc trước không được âm")
    @Max(value = 1440, message = "Chỉ nhắc trước tối đa 1440 phút (1 ngày)")
    private Integer reminderMinutesBefore;

    // Bài học liên quan (không bắt buộc)
    private UUID lessonContentId;
}
