package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.CreateMentorScheduleRequest;
import com.sba301.englishcenter.dto.request.UpdateMentorScheduleRequest;
import com.sba301.englishcenter.dto.response.MentorScheduleResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.MentorScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Mentor Availability", description = "Slot thời gian rảnh của Mentor")
public class MentorScheduleController {

    private final MentorScheduleService mentorScheduleService;

    @PostMapping("/mentor/availability")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor mở slot",
            description = "Mentor đang đăng nhập mở 1 slot rảnh (giờ UTC). Giá slot = hourly_rate x số giờ.")
    public ApiResponse<MentorScheduleResponse> createSlot(
            @Valid @RequestBody CreateMentorScheduleRequest request) {
        return ApiResponse.success(mentorScheduleService.createSlot(currentUserId(), request));
    }

    @GetMapping("/mentor/availability")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor xem slot của mình",
            description = "Các slot chưa kết thúc (mọi trạng thái) của mentor đang đăng nhập, sắp theo giờ bắt đầu")
    public PageResponse<MentorScheduleResponse> getMySlots(@PageableDefault(size = 20) Pageable pageable) {
        return mentorScheduleService.getMySlots(currentUserId(), pageable);
    }

    @PutMapping("/mentor/availability/{scheduleId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor sửa / đóng / mở lại slot",
            description = "Chỉ sửa được slot chưa có người đặt. status: available = mở, cancelled = đóng")
    public ApiResponse<MentorScheduleResponse> updateSlot(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdateMentorScheduleRequest request) {
        return ApiResponse.success(mentorScheduleService.updateSlot(currentUserId(), scheduleId, request));
    }

    @DeleteMapping("/mentor/availability/{scheduleId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor xóa slot", description = "Không xóa được slot còn booking chưa hủy")
    public ApiResponse<Void> deleteSlot(@PathVariable UUID scheduleId) {
        mentorScheduleService.deleteSlot(currentUserId(), scheduleId);
        return ApiResponse.success();
    }

    @GetMapping("/mentors/{mentorId}/availability")
    @Operation(summary = "Xem lịch trống của mentor",
            description = "Slot còn trống và slot đã có người đặt, chưa tới giờ, bắt đầu trong [from, to). "
                    + "from/to dạng ISO-8601 UTC; mặc định from = hiện tại, to = from + 7 ngày")
    public ApiResponse<List<MentorScheduleResponse>> getAvailability(
            @PathVariable UUID mentorId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ApiResponse.success(mentorScheduleService.getAvailability(mentorId, from, to));
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
