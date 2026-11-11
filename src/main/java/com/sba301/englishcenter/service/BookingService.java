package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.dto.request.CancelBookingRequest;
import com.sba301.englishcenter.dto.request.RescheduleBookingRequest;
import com.sba301.englishcenter.dto.request.RescheduleRequestRequest;
import com.sba301.englishcenter.dto.request.UpdateBookingStatusRequest;
import com.sba301.englishcenter.dto.response.BookingResponse;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lịch hẹn 1-1. Không còn đặt slot công khai, học viên không tự đổi lịch (chỉ mentor dời buổi khi bận): buổi học được tạo trong gói kèm 1-1
 * ({@link MentoringPlanService}) sau khi mentor tư vấn, học viên thanh toán cả gói.
 */
public interface BookingService {

    /**
     * Xem chi tiết 1 booking: chỉ học viên / mentor của booking hoặc admin.
     */
    BookingResponse getBooking(UUID requesterId, boolean isAdmin, UUID bookingId);

    /**
     * Học viên hoặc mentor của booking hủy lịch, phải trước giờ học theo thời hạn cấu hình (TASK D5-09).
     */
    BookingResponse cancel(UUID requesterId, UUID bookingId, CancelBookingRequest request);

    /**
     * Mentor cập nhật kết quả buổi học: completed hoặc no_show (TASK D5-11).
     */
    BookingResponse updateStatusByMentor(UUID mentorId, UUID bookingId, UpdateBookingStatusRequest request);

    /**
     * Mentor dời 1 buổi (chưa học) sang giờ khác khi bận (V26): trước giờ học ít nhất N giờ (cấu hình, mặc định 2),
     * giữ nguyên thời lượng / học phí, không trùng lịch mentor và học viên, báo cho học viên.
     */
    BookingResponse rescheduleByMentor(UUID mentorId, UUID bookingId, RescheduleBookingRequest request);

    /**
     * Học viên xin dời 1 buổi đã xác nhận (V28): trước giờ học ít nhất N giờ (như mentor dời), ghi đè yêu cầu cũ,
     * báo mentor. Học viên không tự đổi giờ - mentor quyết định.
     */
    BookingResponse requestReschedule(UUID studentId, UUID bookingId, RescheduleRequestRequest request);

    /** Mentor từ chối yêu cầu dời lịch của học viên (V28): xóa yêu cầu, báo học viên kèm lý do. */
    BookingResponse rejectRescheduleRequest(UUID mentorId, UUID bookingId, String reason);

    /**
     * Lịch của học viên: sắp tới (past = false) hoặc đã qua (past = true) (TASK D5-12).
     */
    PageResponse<BookingResponse> getStudentSchedule(UUID studentId, boolean past, Pageable pageable);

    /**
     * Lịch học (Study Schedule) của học viên: các buổi kèm 1-1 bắt đầu trong [from, to), sắp theo giờ học tăng dần.
     * status = null -> mọi buổi đã thanh toán (confirmed / completed / cancelled / no_show), bỏ buổi chờ thanh toán.
     */
    PageResponse<BookingResponse> getStudentSessions(UUID studentId, LocalDateTime from, LocalDateTime to,
                                                     BookingStatus status, Pageable pageable);

    /**
     * Lịch dạy của mentor: sắp tới (past = false) hoặc đã qua (past = true) (TASK D5-13).
     */
    PageResponse<BookingResponse> getMentorSchedule(UUID mentorId, boolean past, Pageable pageable);

    /**
     * Admin xem, lọc booking (TASK D5-14). Tham số null thì bỏ qua.
     */
    PageResponse<BookingResponse> getAllBookings(BookingStatus status, UUID mentorId, UUID studentId,
                                                 Pageable pageable);

    /**
     * Job nhắc lịch (TASK-D5-02 / D5-18): gửi thông báo "Sắp đến giờ học" cho học viên và mentor
     * của các booking đã xác nhận, bắt đầu trong N phút tới (cấu hình), mỗi booking chỉ nhắc 1 lần.
     *
     * @return số booking vừa được nhắc
     */
    int sendUpcomingReminders();

    /**
     * Admin hủy booking bất thường, không áp dụng thời hạn hủy (TASK D5-14).
     */
    BookingResponse cancelByAdmin(UUID adminId, UUID bookingId, CancelBookingRequest request);
}
