package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.StudyStatus;
import com.sba301.englishcenter.dto.request.StudyScheduleRequest;
import com.sba301.englishcenter.dto.request.UpdateStudyStatusRequest;
import com.sba301.englishcenter.dto.response.StudyScheduleResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.StudyScheduleService;
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
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/study-schedules")
@RequiredArgsConstructor
// Lịch học cá nhân: người học (USER chưa mua khóa / STUDENT)
@PreAuthorize("hasAnyRole('USER', 'STUDENT')")
@Tag(name = "Study Schedule", description = "Lịch nhắc học tập cá nhân (TASK-D5-01). Thời gian ISO-8601 UTC")
public class StudyScheduleController {

    private final StudyScheduleService studyScheduleService;

    @GetMapping
    @Operation(summary = "Lịch học của tôi",
            description = "Lịch trong [from, to) (mặc định từ đầu hôm nay UTC tới 30 ngày sau), sắp theo giờ học; lọc status nếu có")
    public PageResponse<StudyScheduleResponse> getMySchedules(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) StudyStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return studyScheduleService.getMySchedules(currentUserId(), from, to, status, pageable);
    }

    @PostMapping
    @Operation(summary = "Tạo lịch học", description = "Thời gian phải ở tương lai; repeatRule none/daily/weekly; nhắc trước 0-1440 phút")
    public ApiResponse<StudyScheduleResponse> create(@Valid @RequestBody StudyScheduleRequest request) {
        return ApiResponse.success(studyScheduleService.create(currentUserId(), request));
    }

    @PutMapping("/{scheduleId}")
    @Operation(summary = "Sửa lịch học", description = "Chỉ lịch của chính mình; đổi giờ thì giờ mới phải ở tương lai")
    public ApiResponse<StudyScheduleResponse> update(@PathVariable UUID scheduleId,
                                                     @Valid @RequestBody StudyScheduleRequest request) {
        return ApiResponse.success(studyScheduleService.update(currentUserId(), scheduleId, request));
    }

    @PutMapping("/{scheduleId}/status")
    @Operation(summary = "Đánh dấu lịch học", description = "done / skipped / cancelled / pending")
    public ApiResponse<StudyScheduleResponse> updateStatus(@PathVariable UUID scheduleId,
                                                           @Valid @RequestBody UpdateStudyStatusRequest request) {
        return ApiResponse.success(studyScheduleService.updateStatus(currentUserId(), scheduleId, request));
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "Xóa lịch học")
    public ApiResponse<Void> delete(@PathVariable UUID scheduleId) {
        studyScheduleService.delete(currentUserId(), scheduleId);
        return ApiResponse.success();
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
