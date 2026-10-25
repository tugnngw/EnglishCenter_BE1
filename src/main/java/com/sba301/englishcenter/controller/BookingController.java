package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.request.BookScheduleRequest;
import com.sba301.englishcenter.dto.request.CancelBookingRequest;
import com.sba301.englishcenter.dto.request.RescheduleBookingRequest;
import com.sba301.englishcenter.dto.request.UpdateBookingStatusRequest;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.BookingService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Booking", description = "Đặt lịch 1-1 với Mentor (TASK D5-06 -> D5-14)")
public class BookingController {

    // type = "past" -> lịch đã qua, giá trị khác -> lịch sắp tới
    private static final String TYPE_PAST = "past";

    private final BookingService bookingService;

    // ===================== STUDENT =====================

    @PostMapping("/bookings")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Học viên đặt slot", description = "Đặt 1 slot còn trống: booking pending, giữ slot và tạo đơn SePay (paymentOrderCode). Thanh toán xong booking mới confirmed")
    public ApiResponse<BookingResponse> book(@Valid @RequestBody BookScheduleRequest request) {
        return ApiResponse.success(bookingService.book(currentUserId(), request));
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "Chi tiết lịch hẹn", description = "Chỉ học viên / mentor của lịch hẹn hoặc Admin")
    public ApiResponse<BookingResponse> getBooking(@PathVariable UUID bookingId) {
        UserPrincipal principal = currentPrincipal();
        boolean isAdmin = RoleName.ADMIN.getValue().equals(principal.getRoleName());
        return ApiResponse.success(bookingService.getBooking(principal.getUserId(), isAdmin, bookingId));
    }

    @PutMapping("/bookings/{bookingId}/cancel")
    @PreAuthorize("hasAnyRole('STUDENT', 'MENTOR')")
    @Operation(summary = "Hủy lịch hẹn",
            description = "Học viên hoặc mentor của lịch hẹn hủy, phải trước giờ học theo thời hạn cấu hình")
    public ApiResponse<BookingResponse> cancel(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return ApiResponse.success(bookingService.cancel(currentUserId(), bookingId, request));
    }

    @PutMapping("/bookings/{bookingId}/reschedule")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Đổi lịch hẹn", description = "Hủy lịch cũ và đặt slot mới của cùng mentor trong 1 transaction")
    public ApiResponse<BookingResponse> reschedule(
            @PathVariable UUID bookingId,
            @Valid @RequestBody RescheduleBookingRequest request) {
        return ApiResponse.success(bookingService.reschedule(currentUserId(), bookingId, request));
    }

    @GetMapping("/me/schedule")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Lịch của tôi (học viên)", description = "type = upcoming (mặc định) hoặc past")
    public PageResponse<BookingResponse> getMySchedule(
            @RequestParam(defaultValue = "upcoming") String type,
            @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.getStudentSchedule(currentUserId(), TYPE_PAST.equalsIgnoreCase(type), pageable);
    }

    // ===================== MENTOR =====================

    @GetMapping("/mentor/schedule")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Lịch dạy của mentor", description = "type = upcoming (mặc định) hoặc past")
    public PageResponse<BookingResponse> getMentorSchedule(
            @RequestParam(defaultValue = "upcoming") String type,
            @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.getMentorSchedule(currentUserId(), TYPE_PAST.equalsIgnoreCase(type), pageable);
    }

    @PutMapping("/mentor/bookings/{bookingId}/status")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor cập nhật kết quả buổi học",
            description = "status = completed hoặc no_show, chỉ khi buổi học đã bắt đầu")
    public ApiResponse<BookingResponse> updateStatus(
            @PathVariable UUID bookingId,
            @Valid @RequestBody UpdateBookingStatusRequest request) {
        return ApiResponse.success(bookingService.updateStatusByMentor(currentUserId(), bookingId, request));
    }

    // ===================== ADMIN =====================

    @GetMapping("/admin/bookings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin xem danh sách lịch hẹn", description = "Lọc theo status, mentorId, studentId")
    public PageResponse<BookingResponse> getAllBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) UUID mentorId,
            @RequestParam(required = false) UUID studentId,
            @PageableDefault(size = 20) Pageable pageable) {
        return bookingService.getAllBookings(status, mentorId, studentId, pageable);
    }

    @PutMapping("/admin/bookings/{bookingId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin hủy lịch hẹn bất thường", description = "Không áp dụng thời hạn hủy")
    public ApiResponse<BookingResponse> cancelByAdmin(
            @PathVariable UUID bookingId,
            @Valid @RequestBody(required = false) CancelBookingRequest request) {
        return ApiResponse.success(bookingService.cancelByAdmin(currentUserId(), bookingId, request));
    }

    private UserPrincipal currentPrincipal() {
        return SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private UUID currentUserId() {
        return currentPrincipal().getUserId();
    }
}
