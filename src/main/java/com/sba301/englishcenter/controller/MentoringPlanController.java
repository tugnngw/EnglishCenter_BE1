package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.request.CancelBookingRequest;
import com.sba301.englishcenter.dto.request.CreateMentoringPlanRequest;
import com.sba301.englishcenter.dto.response.MentoringPlanResponse;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.MentoringPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Mentoring Plan", description = "Gói kèm 1-1 theo lộ trình (mentor tạo sau khi tư vấn, học viên trả 1 lần cả gói)")
public class MentoringPlanController {

    private final MentoringPlanService mentoringPlanService;

    // ===================== MENTOR =====================

    @PostMapping("/mentor/mentoring-plans")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor tạo gói kèm 1-1",
            description = "Tạo N buổi giữ riêng cho học viên (booking pending). Học viên thanh toán cả gói thì các buổi mới được xác nhận")
    public ApiResponse<MentoringPlanResponse> create(@Valid @RequestBody CreateMentoringPlanRequest request) {
        return ApiResponse.success(mentoringPlanService.createPlan(currentUserId(), request));
    }

    @GetMapping("/mentor/mentoring-plans")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Các gói mentor đã tạo")
    public PageResponse<MentoringPlanResponse> getMentorPlans(@PageableDefault(size = 10) Pageable pageable) {
        return mentoringPlanService.getMentorPlans(currentUserId(), pageable);
    }

    @PutMapping("/mentor/mentoring-plans/{planId}/cancel")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor hủy gói chưa thanh toán")
    public ApiResponse<MentoringPlanResponse> cancel(@PathVariable UUID planId,
                                                     @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return ApiResponse.success(mentoringPlanService.cancelByMentor(currentUserId(), planId,
                request == null ? null : request.getReason()));
    }

    // ===================== HỌC VIÊN =====================

    @GetMapping("/me/mentoring-plans")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Các gói kèm 1-1 của tôi")
    public ApiResponse<List<MentoringPlanResponse>> getMyPlans() {
        return ApiResponse.success(mentoringPlanService.getMyPlans(currentUserId()));
    }

    @PostMapping("/me/mentoring-plans/{planId}/checkout")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Thanh toán cả gói", description = "Tạo (hoặc lấy lại) đơn SePay MENTOR_PLAN, FE mở QR theo orderCode")
    public ApiResponse<PaymentOrderResponse> checkout(@PathVariable UUID planId) {
        return ApiResponse.success(mentoringPlanService.checkout(currentUserId(), planId));
    }

    @PutMapping("/me/mentoring-plans/{planId}/reject")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Học viên từ chối gói chưa thanh toán")
    public ApiResponse<MentoringPlanResponse> reject(@PathVariable UUID planId,
                                                     @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return ApiResponse.success(mentoringPlanService.rejectByStudent(currentUserId(), planId,
                request == null ? null : request.getReason()));
    }

    // ===================== CHUNG =====================

    @GetMapping("/mentoring-plans/{planId}")
    @Operation(summary = "Chi tiết gói", description = "Chỉ mentor / học viên của gói hoặc Admin")
    public ApiResponse<MentoringPlanResponse> getPlan(@PathVariable UUID planId) {
        UserPrincipal principal = currentPrincipal();
        boolean isAdmin = RoleName.ADMIN.getValue().equals(principal.getRoleName());
        return ApiResponse.success(mentoringPlanService.getPlan(principal.getUserId(), isAdmin, planId));
    }

    private UserPrincipal currentPrincipal() {
        return SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private UUID currentUserId() {
        return currentPrincipal().getUserId();
    }
}
