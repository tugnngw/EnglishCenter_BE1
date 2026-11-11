package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.StudyScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me/study-schedules")
@RequiredArgsConstructor
// Người học (USER chưa mua gói thì danh sách rỗng / STUDENT)
@PreAuthorize("hasAnyRole('USER', 'STUDENT')")
@Tag(name = "Study Schedule", description = "Lịch học của học viên = các buổi kèm 1-1 mentor xếp. Thời gian ISO-8601 UTC")
public class StudyScheduleController {

    private final StudyScheduleService studyScheduleService;

    @GetMapping
    @Operation(summary = "Lịch học của tôi",
            description = "Các buổi kèm 1-1 bắt đầu trong [from, to) (mặc định từ đầu hôm nay UTC tới 30 ngày sau), "
                    + "sắp theo giờ học; status = confirmed / completed / cancelled / no_show (bỏ trống = tất cả)")
    public PageResponse<BookingResponse> getMySchedules(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return studyScheduleService.getMySchedules(currentUserId(), from, to, status, pageable);
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
