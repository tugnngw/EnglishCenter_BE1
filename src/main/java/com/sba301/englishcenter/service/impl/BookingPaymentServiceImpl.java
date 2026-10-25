package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.common.enums.ScheduleStatus;
import com.sba301.englishcenter.entity.Booking;
import com.sba301.englishcenter.entity.MentorSchedule;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.BookingRepository;
import com.sba301.englishcenter.repository.MentorScheduleRepository;
import com.sba301.englishcenter.service.BookingPaymentService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Xử lý booking theo kết quả thanh toán SePay (đơn MENTOR_SESSION).
 * Không phụ thuộc PaymentService để tránh vòng phụ thuộc: BookingServiceImpl -> PaymentService -> class này.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingPaymentServiceImpl implements BookingPaymentService {

    private final BookingRepository bookingRepository;
    private final MentorScheduleRepository mentorScheduleRepository;
    private final NotificationService notificationService;
    private final UserService userService;

    @Override
    @Transactional
    public UUID confirmPaidBooking(UUID bookingId) {
        Booking booking = findBooking(bookingId);
        // Khóa slot trước khi đổi trạng thái (cùng thứ tự khóa với hủy / đổi lịch)
        MentorSchedule schedule = lockSlot(booking.getSchedule().getScheduleId());
        if (booking.getStatus() != BookingStatus.pending) {
            log.warn("Booking {} is {} when payment arrived -> review", bookingId, booking.getStatus());
            return null;
        }

        booking.setStatus(BookingStatus.confirmed);
        bookingRepository.save(booking);
        log.info("Booking {} confirmed after SePay payment", bookingId);

        // Thông báo giống lúc đặt lịch thành công (TASK D5-16)
        String studentName = userService.getUserById(booking.getStudentId()).getFullName();
        String mentorName = userService.getUserById(booking.getMentorId()).getFullName();
        notificationService.notifyMentorSession(booking.getStudentId(), "Đặt lịch thành công",
                "Đã nhận thanh toán. Buổi kèm 1-1 với mentor " + mentorName + " đã được xác nhận.",
                schedule.getScheduleId(), bookingId);
        notificationService.notifyMentorSession(booking.getMentorId(), "Có lịch hẹn mới",
                "Học viên " + studentName + " vừa đặt và thanh toán một buổi kèm 1-1 với bạn.",
                schedule.getScheduleId(), bookingId);
        return schedule.getScheduleId();
    }

    @Override
    @Transactional
    public void releaseUnpaidBooking(UUID bookingId, String reason) {
        Booking booking = findBooking(bookingId);
        MentorSchedule schedule = lockSlot(booking.getSchedule().getScheduleId());
        if (booking.getStatus() != BookingStatus.pending) {
            return;
        }

        // Hệ thống hủy (cancelled_by = null), mở lại slot cho người khác đặt
        booking.setStatus(BookingStatus.cancelled);
        booking.setCancelReason(reason);
        booking.setCancelledAt(UtcTimeUtil.nowUtc());
        schedule.setStatus(ScheduleStatus.available);
        bookingRepository.save(booking);
        mentorScheduleRepository.save(schedule);
        log.info("Unpaid booking {} released: {}", bookingId, reason);

        notificationService.notifyMentorSession(booking.getStudentId(), "Lịch hẹn đã bị hủy",
                "Lịch hẹn kèm 1-1 chưa được thanh toán nên đã bị hủy. Lý do: " + reason,
                schedule.getScheduleId(), bookingId);
    }

    private Booking findBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
    }

    private MentorSchedule lockSlot(UUID scheduleId) {
        return mentorScheduleRepository.findWithLockByScheduleId(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));
    }
}
