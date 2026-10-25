package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.common.enums.ScheduleStatus;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.entity.Booking;
import com.sba301.englishcenter.entity.MentorSchedule;
import com.sba301.englishcenter.repository.BookingRepository;
import com.sba301.englishcenter.repository.MentorScheduleRepository;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPaymentServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MentorScheduleRepository mentorScheduleRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private BookingPaymentServiceImpl bookingPaymentService;

    private final UUID mentorId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();
    private MentorSchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = MentorSchedule.builder()
                .scheduleId(UUID.randomUUID())
                .mentorId(mentorId)
                .startTime(UtcTimeUtil.nowUtc().plusDays(2))
                .endTime(UtcTimeUtil.nowUtc().plusDays(2).plusHours(1))
                .price(new BigDecimal("200000.00"))
                .status(ScheduleStatus.booked)
                .build();
        lenient().when(mentorScheduleRepository.findWithLockByScheduleId(schedule.getScheduleId()))
                .thenReturn(Optional.of(schedule));
        lenient().when(userService.getUserById(any()))
                .thenAnswer(inv -> UserResponse.builder().userId(inv.getArgument(0)).fullName("User").build());
    }

    private Booking booking(BookingStatus status) {
        Booking booking = Booking.builder()
                .bookingId(bookingId)
                .schedule(schedule)
                .mentorId(mentorId)
                .studentId(studentId)
                .status(status)
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        return booking;
    }

    @Test
    @DisplayName("Thanh toán thành công: booking pending -> confirmed, gửi thông báo cho học viên và mentor")
    void confirmPaidBooking_success() {
        Booking booking = booking(BookingStatus.pending);

        UUID result = bookingPaymentService.confirmPaidBooking(bookingId);

        assertThat(result).isEqualTo(schedule.getScheduleId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.confirmed);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.booked);
        verify(notificationService).notifyMentorSession(eq(studentId), anyString(), anyString(), eq(schedule.getScheduleId()), eq(bookingId));
        verify(notificationService).notifyMentorSession(eq(mentorId), anyString(), anyString(), eq(schedule.getScheduleId()), eq(bookingId));
    }

    @Test
    @DisplayName("Tiền về khi booking đã bị hủy: trả null để đơn chuyển REVIEW_REQUIRED, không đổi booking")
    void confirmPaidBooking_bookingCancelled() {
        Booking booking = booking(BookingStatus.cancelled);

        assertThat(bookingPaymentService.confirmPaidBooking(bookingId)).isNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.cancelled);
        verify(notificationService, never()).notifyMentorSession(any(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Đơn hết hạn: booking pending -> cancelled (hệ thống hủy), slot mở lại")
    void releaseUnpaidBooking_success() {
        Booking booking = booking(BookingStatus.pending);

        bookingPaymentService.releaseUnpaidBooking(bookingId, "Hết hạn thanh toán");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.cancelled);
        assertThat(booking.getCancelReason()).isEqualTo("Hết hạn thanh toán");
        assertThat(booking.getCancelledBy()).isNull();
        assertThat(booking.getCancelledAt()).isNotNull();
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.available);
        verify(notificationService).notifyMentorSession(eq(studentId), anyString(), anyString(), eq(schedule.getScheduleId()), eq(bookingId));
    }

    @Test
    @DisplayName("Booking đã thanh toán thì không bị trả slot")
    void releaseUnpaidBooking_confirmedIgnored() {
        Booking booking = booking(BookingStatus.confirmed);

        bookingPaymentService.releaseUnpaidBooking(bookingId, "Hết hạn thanh toán");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.confirmed);
        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.booked);
        verify(bookingRepository, never()).save(any());
    }
}
