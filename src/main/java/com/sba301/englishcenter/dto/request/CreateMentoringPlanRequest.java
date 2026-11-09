package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.validation.MeetingUrl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Mentor tạo gói kèm 1-1 cho 1 học viên sau khi tư vấn chốt lộ trình.
 * Chọn lộ trình có total_sessions thì số buổi phải đúng bằng số buổi của lộ trình.
 * Giá mỗi buổi = hourly_rate của mentor x số giờ, học viên thanh toán 1 lần cả gói.
 */
@Getter
@Setter
public class CreateMentoringPlanRequest {

    @NotNull(message = "Học viên không được để trống")
    private UUID studentId;

    private UUID learningPathId;

    // Yêu cầu tư vấn dẫn tới gói (nếu có) -> chuyển sang "agreed"
    private UUID consultationId;

    @NotBlank(message = "Tên gói không được để trống")
    @Size(max = 255, message = "Tên gói không được vượt quá 255 ký tự")
    private String title;

    @Size(max = 2000, message = "Ghi chú không được vượt quá 2000 ký tự")
    private String note;

    @Size(max = 500, message = "Link meeting không được vượt quá 500 ký tự")
    @MeetingUrl
    private String meetingUrl;

    @NotEmpty(message = "Gói phải có ít nhất 1 buổi")
    @Size(max = 60, message = "Gói tối đa 60 buổi")
    private List<@Valid @NotNull PlanSessionRequest> sessions;
}
