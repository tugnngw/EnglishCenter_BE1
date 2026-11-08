package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.common.enums.PaymentOrderType;
import com.sba301.englishcenter.common.enums.ScheduleStatus;
import com.sba301.englishcenter.dto.request.CancelBookingRequest;
import com.sba301.englishcenter.dto.request.UpdateBookingStatusRequest;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.entity.Booking;
import com.sba301.englishcenter.entity.MentorSchedule;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.BookingRepository;
import com.sba301.englishcenter.repository.MentorScheduleRepository;
import com.sba301.englishcenter.repository.MentoringPlanRepository;
import com.sba301.englishcenter.repository.PaymentOrderRepository;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.PaymentService;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    private static final long CANCEL_DEADLINE_HOURS = 24;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MentorScheduleRepository mentorScheduleRepository;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private MentoringPlanRepository mentoringPlanRepository;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private final UUID mentorId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private static final String ORDER_CODE = "EC1000000001";

    @BeforeEach
    void setUp() {
        // Giá trị app.booking.cancel-deadline-hours trong application.properties
        ReflectionTestUtils.setField(bookingService, "cancelDeadlineHours", CANCEL_DEADLINE_HOURS);
        ReflectionTestUtils.setField(bookingService, "reminderMinutesBefore", 30L);
        // Họ tên dùng để ghi thông báo / response, không phải test nào cũng cần
        lenient().when(userService.getUserById(any()))
                .thenAnswer(inv -> UserResponse.builder().userId(inv.getArgument(0)).fullName("User").build());
        // Đơn SePay Dev 4 tạo cho booking pending
        lenient().when(paymentService.createMentorSessionOrder(any(), any(), any(), anyString()))
                .thenAnswer(inv -> sessionOrder(inv.getArgument(1), inv.getArgument(2)));
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> {
            Booking booking = inv.getArgument(0);
            if (booking.getBookingId() == null) {
                booking.setBookingId(UUID.randomUUID());
            }
            return booking;
        });
    }

    private PaymentOrderResponse sessionOrder(UUID orderBookingId, BigDecimal amount) {
        return new PaymentOrderResponse(UUID.randomUUID(), ORDER_CODE, studentId, null, "Buổi kèm 1-1", amount, "VND",
                PaymentOrderStatus.PENDING, Instant.now(), Instant.now().plusSeconds(900), null, "MBBank", "0000000001",
                "TEST", ORDER_CODE, "https://vietqr.app/img", PaymentOrderType.MENTOR_SESSION, orderBookingId, null, null, null);
    }

    // Slot của mentor, bắt đầu sau "hoursFromNow" giờ (giờ UTC)
    private MentorSchedule slot(ScheduleStatus status, long hoursFromNow, UUID ownerMentorId) {
        LocalDateTime startTime = UtcTimeUtil.nowUtc().plusHours(hoursFromNow).withNano(0);
        return MentorSchedule.builder()
                .scheduleId(UUID.randomUUID())
                .mentorId(ownerMentorId)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .price(new BigDecimal("200000.00"))
                .meetingUrl("https://meet.google.com/abc-defg-hij")
                .status(status)
                .build();
    }

    private Booking booking(MentorSchedule schedule, BookingStatus status) {
        return Booking.builder()
                .bookingId(bookingId)
                .schedule(schedule)
                .mentorId(schedule.getMentorId())
                .studentId(studentId)
                .status(status)
                .build();
    }

    private CancelBookingRequest cancelRequest(String reason) {
        CancelBookingRequest request = new CancelBookingRequest();
        request.setReason(reason);
        return request;
    }

    private UpdateBookingStatusRequest statusRequest(BookingStatus status) {
        UpdateBookingStatusRequest request = new UpdateBookingStatusRequest();
        request.setStatus(status);
        return request;
    }

    // ===================== book =====================

    // ===================== cancel =====================

    @Test
    @DisplayName("Học viên hủy trước thời hạn: booking cancelled, slot mở lại, thông báo cho 2 bên")
    void cancel_success() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, 48, mentorId);
        Booking booking = booking(schedule, BookingStatus.confirmed);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(mentorScheduleRepository.findWithLockByScheduleId(schedule.getScheduleId()))
                .thenReturn(Optional.of(schedule));

        BookingResponse response = bookingService.cancel(studentId, bookingId, cancelRequest("Bận việc"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.cancelled);
        assertThat(booking.getCancelReason()).isEqualTo("Bận việc");
        assertThat(booking.getCancelledBy()).isEqualTo(studentId);
        assertThat(booking.getCancelledAt()).isNotNull();
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.available);
        assertThat(response.getStatus()).isEqualTo("cancelled");
        verify(notificationService, times(2)).notifyMentorSession(any(), anyString(), anyString(), any(), eq(bookingId));
    }

    @Test
    @DisplayName("Hủy khi còn chưa tới 24 giờ trước giờ học -> BOOKING_CANCEL_DEADLINE_PASSED")
    void cancel_deadlinePassed() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, CANCEL_DEADLINE_HOURS - 1, mentorId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(schedule, BookingStatus.confirmed)));

        assertThatThrownBy(() -> bookingService.cancel(studentId, bookingId, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_CANCEL_DEADLINE_PASSED);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.booked);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Hủy booking đã hủy trước đó -> BOOKING_NOT_CANCELLABLE")
    void cancel_alreadyCancelled() {
        MentorSchedule schedule = slot(ScheduleStatus.available, 48, mentorId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(schedule, BookingStatus.cancelled)));

        assertThatThrownBy(() -> bookingService.cancel(studentId, bookingId, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_NOT_CANCELLABLE);
    }

    @Test
    @DisplayName("Người không thuộc booking hủy -> BOOKING_NOT_FOUND")
    void cancel_notParticipant() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, 48, mentorId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(schedule, BookingStatus.confirmed)));

        assertThatThrownBy(() -> bookingService.cancel(UUID.randomUUID(), bookingId, null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_NOT_FOUND);
    }

    @Test
    @DisplayName("Admin hủy booking sát giờ học: không áp dụng thời hạn")
    void cancelByAdmin_ignoreDeadline() {
        UUID adminId = UUID.randomUUID();
        MentorSchedule schedule = slot(ScheduleStatus.booked, 1, mentorId);
        Booking booking = booking(schedule, BookingStatus.confirmed);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(mentorScheduleRepository.findWithLockByScheduleId(schedule.getScheduleId()))
                .thenReturn(Optional.of(schedule));

        bookingService.cancelByAdmin(adminId, bookingId, cancelRequest("Mentor báo nghỉ"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.cancelled);
        assertThat(booking.getCancelledBy()).isEqualTo(adminId);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.available);
    }

    // ===================== reschedule =====================

    // ===================== updateStatusByMentor =====================

    @Test
    @DisplayName("Mentor đánh dấu đã học sau giờ bắt đầu: booking completed, slot completed")
    void updateStatus_completed() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, -2, mentorId);
        Booking booking = booking(schedule, BookingStatus.confirmed);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.updateStatusByMentor(
                mentorId, bookingId, statusRequest(BookingStatus.completed));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.completed);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.completed);
        assertThat(response.getStatus()).isEqualTo("completed");
    }

    @Test
    @DisplayName("Mentor đánh dấu vắng mặt khi buổi học chưa bắt đầu -> BOOKING_NOT_STARTED")
    void updateStatus_notStarted() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, 5, mentorId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(schedule, BookingStatus.confirmed)));

        assertThatThrownBy(() -> bookingService.updateStatusByMentor(
                mentorId, bookingId, statusRequest(BookingStatus.no_show)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_NOT_STARTED);
    }

    @Test
    @DisplayName("Mentor gửi status cancelled qua API kết quả -> BOOKING_STATUS_INVALID")
    void updateStatus_invalidStatus() {
        assertThatThrownBy(() -> bookingService.updateStatusByMentor(
                mentorId, bookingId, statusRequest(BookingStatus.cancelled)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_STATUS_INVALID);
        verify(bookingRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Mentor khác cập nhật booking -> BOOKING_NOT_FOUND")
    void updateStatus_otherMentor() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, -2, mentorId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(schedule, BookingStatus.confirmed)));

        assertThatThrownBy(() -> bookingService.updateStatusByMentor(
                UUID.randomUUID(), bookingId, statusRequest(BookingStatus.completed)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_NOT_FOUND);
    }

    // ===================== thanh toán (Dev 5 x Dev 4) =====================

    @Test
    @DisplayName("Hủy booking chưa thanh toán: hủy đơn SePay đang chờ, slot mở lại")
    void cancel_pendingBooking_cancelsPaymentOrder() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, 48, mentorId);
        Booking booking = booking(schedule, BookingStatus.pending);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(mentorScheduleRepository.findWithLockByScheduleId(schedule.getScheduleId()))
                .thenReturn(Optional.of(schedule));

        bookingService.cancel(studentId, bookingId, null);

        verify(paymentService).cancelPendingBookingOrder(bookingId);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.cancelled);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.available);
    }

    @Test
    @DisplayName("Hủy booking đã thanh toán: không đụng tới đơn thanh toán (không hoàn tiền)")
    void cancel_paidBooking_keepsPayment() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, 48, mentorId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(schedule, BookingStatus.confirmed)));
        when(mentorScheduleRepository.findWithLockByScheduleId(schedule.getScheduleId()))
                .thenReturn(Optional.of(schedule));

        bookingService.cancel(studentId, bookingId, null);

        verify(paymentService, never()).cancelPendingBookingOrder(any());
    }

    @Test
    @DisplayName("Mentor ghi nhận kết quả cho booking chưa thanh toán -> BOOKING_STATUS_INVALID")
    void updateStatus_pendingBooking() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, -2, mentorId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(schedule, BookingStatus.pending)));

        assertThatThrownBy(() -> bookingService.updateStatusByMentor(
                mentorId, bookingId, statusRequest(BookingStatus.completed)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_STATUS_INVALID);
    }

    // ===================== nhắc lịch (TASK-D5-02 / D5-18) =====================

    @Test
    @DisplayName("Nhắc lịch: booking confirmed sắp bắt đầu -> thông báo cho học viên + mentor, đánh dấu đã nhắc")
    void sendUpcomingReminders_notifiesBothOnce() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, 0, mentorId);
        schedule.setStartTime(UtcTimeUtil.nowUtc().plusMinutes(20));
        Booking booking = booking(schedule, BookingStatus.confirmed);
        when(bookingRepository
                .findByStatusAndReminderSentAtIsNullAndSchedule_StartTimeGreaterThanAndSchedule_StartTimeLessThanEqual(
                        eq(BookingStatus.confirmed), any(), any()))
                .thenReturn(java.util.List.of(booking));

        int sent = bookingService.sendUpcomingReminders();

        assertThat(sent).isEqualTo(1);
        assertThat(booking.getReminderSentAt()).isNotNull();
        verify(notificationService).notifyMentorSession(eq(studentId), eq("Sắp đến giờ học"), anyString(),
                eq(schedule.getScheduleId()), eq(bookingId));
        verify(notificationService).notifyMentorSession(eq(mentorId), eq("Sắp đến giờ học"), anyString(),
                eq(schedule.getScheduleId()), eq(bookingId));
    }

    @Test
    @DisplayName("Nhắc lịch: tìm đúng khoảng (bây giờ, bây giờ + 30 phút]; không có booking thì không gửi gì")
    void sendUpcomingReminders_window() {
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        when(bookingRepository
                .findByStatusAndReminderSentAtIsNullAndSchedule_StartTimeGreaterThanAndSchedule_StartTimeLessThanEqual(
                        eq(BookingStatus.confirmed), from.capture(), to.capture()))
                .thenReturn(java.util.List.of());

        assertThat(bookingService.sendUpcomingReminders()).isZero();
        assertThat(java.time.Duration.between(from.getValue(), to.getValue()).toMinutes()).isEqualTo(30);
        verify(notificationService, never()).notifyMentorSession(any(), anyString(), anyString(), any(), any());
    }

    // ===================== getBooking =====================

    @Test
    @DisplayName("Người ngoài xem booking -> BOOKING_NOT_FOUND, admin thì xem được")
    void getBooking_access() {
        MentorSchedule schedule = slot(ScheduleStatus.booked, 48, mentorId);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking(schedule, BookingStatus.confirmed)));

        assertThatThrownBy(() -> bookingService.getBooking(UUID.randomUUID(), false, bookingId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOKING_NOT_FOUND);
        assertThat(bookingService.getBooking(UUID.randomUUID(), true, bookingId).getBookingId()).isEqualTo(bookingId);
        assertThat(bookingService.getBooking(mentorId, false, bookingId).getMeetingUrl()).isNotNull();
        assertThat(bookingService.getBooking(studentId, false, bookingId).getStudentId()).isEqualTo(studentId);
    }
}
