package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.common.enums.ScheduleStatus;
import com.sba301.englishcenter.dto.request.CreateMentorScheduleRequest;
import com.sba301.englishcenter.dto.request.UpdateMentorScheduleRequest;
import com.sba301.englishcenter.dto.response.MentorResponse;
import com.sba301.englishcenter.dto.response.MentorScheduleResponse;
import com.sba301.englishcenter.entity.MentorSchedule;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.BookingRepository;
import com.sba301.englishcenter.repository.MentorScheduleRepository;
import com.sba301.englishcenter.service.MentorService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorScheduleServiceImplTest {

    @Mock
    private MentorScheduleRepository mentorScheduleRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private MentorService mentorService;

    @InjectMocks
    private MentorScheduleServiceImpl mentorScheduleService;

    private final UUID mentorId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();
    // Giờ bắt đầu ở tương lai, tính theo UTC (TASK D5-19)
    private final Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    private CreateMentorScheduleRequest createRequest(Instant startTime, Instant endTime) {
        CreateMentorScheduleRequest request = new CreateMentorScheduleRequest();
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setMeetingUrl("https://meet.google.com/abc-defg-hij");
        return request;
    }

    private UpdateMentorScheduleRequest updateRequest(ScheduleStatus status) {
        UpdateMentorScheduleRequest request = new UpdateMentorScheduleRequest();
        request.setStartTime(start);
        request.setEndTime(start.plus(Duration.ofHours(2)));
        request.setMeetingUrl("https://meet.google.com/new-link");
        request.setStatus(status);
        return request;
    }

    private MentorResponse mentor(BigDecimal hourlyRate) {
        return MentorResponse.builder().userId(mentorId).hourlyRate(hourlyRate).build();
    }

    private MentorSchedule slot(ScheduleStatus status) {
        LocalDateTime startTime = UtcTimeUtil.toUtc(start);
        return MentorSchedule.builder()
                .scheduleId(scheduleId)
                .mentorId(mentorId)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .price(new BigDecimal("200000.00"))
                .meetingUrl("https://meet.google.com/abc-defg-hij")
                .status(status)
                .build();
    }

    // ===================== createSlot =====================

    @Test
    @DisplayName("Mở slot thành công: lưu giờ UTC, giá = hourly_rate x số giờ, trạng thái available")
    void createSlot_success() {
        when(mentorService.getMentorById(mentorId)).thenReturn(mentor(new BigDecimal("200000")));
        when(mentorScheduleRepository.existsByMentorIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                any(), any(), any(), any())).thenReturn(false);
        when(mentorScheduleRepository.save(any(MentorSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        MentorScheduleResponse response = mentorScheduleService.createSlot(
                mentorId, createRequest(start, start.plus(Duration.ofMinutes(90))));

        ArgumentCaptor<MentorSchedule> captor = ArgumentCaptor.forClass(MentorSchedule.class);
        verify(mentorScheduleRepository).save(captor.capture());
        MentorSchedule saved = captor.getValue();
        assertThat(saved.getMentorId()).isEqualTo(mentorId);
        assertThat(saved.getStartTime()).isEqualTo(UtcTimeUtil.toUtc(start));
        assertThat(saved.getStatus()).isEqualTo(ScheduleStatus.available);
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("300000.00"));
        assertThat(response.getStatus()).isEqualTo("available");
        assertThat(response.getStartTime()).isEqualTo(start);
    }

    @Test
    @DisplayName("Giờ kết thúc không sau giờ bắt đầu -> INVALID_SCHEDULE_TIME")
    void createSlot_invalidTime() {
        assertThatThrownBy(() -> mentorScheduleService.createSlot(mentorId, createRequest(start, start)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SCHEDULE_TIME);
        verify(mentorScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mentor bị thu hồi/khóa -> MENTOR_PROFILE_NOT_FOUND")
    void createSlot_mentorNotFound() {
        when(mentorService.getMentorById(mentorId))
                .thenThrow(new AppException(ErrorCode.MENTOR_PROFILE_NOT_FOUND));

        assertThatThrownBy(() -> mentorScheduleService.createSlot(
                mentorId, createRequest(start, start.plus(Duration.ofHours(1)))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MENTOR_PROFILE_NOT_FOUND);
        verify(mentorScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mentor chưa có hourly_rate -> MENTOR_HOURLY_RATE_NOT_SET")
    void createSlot_hourlyRateNotSet() {
        when(mentorService.getMentorById(mentorId)).thenReturn(mentor(null));

        assertThatThrownBy(() -> mentorScheduleService.createSlot(
                mentorId, createRequest(start, start.plus(Duration.ofHours(1)))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MENTOR_HOURLY_RATE_NOT_SET);
        verify(mentorScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Trùng giờ với slot khác của mentor -> SCHEDULE_TIME_CONFLICT")
    void createSlot_overlap() {
        when(mentorService.getMentorById(mentorId)).thenReturn(mentor(new BigDecimal("200000")));
        when(mentorScheduleRepository.existsByMentorIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                any(), eq(ScheduleStatus.cancelled), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> mentorScheduleService.createSlot(
                mentorId, createRequest(start, start.plus(Duration.ofHours(1)))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_TIME_CONFLICT);
        verify(mentorScheduleRepository, never()).save(any());
    }

    // ===================== getMySlots =====================

    @Test
    @DisplayName("Mentor xem slot của mình: chỉ slot chưa kết thúc, luôn sắp theo startTime")
    void getMySlots_sortedByStartTime() {
        Page<MentorSchedule> page = new PageImpl<>(List.of(slot(ScheduleStatus.booked)));
        when(mentorScheduleRepository.findByMentorIdAndEndTimeAfter(eq(mentorId), any(), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<MentorScheduleResponse> result = mentorScheduleService.getMySlots(
                mentorId, PageRequest.of(0, 20, Sort.by("price")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(mentorScheduleRepository).findByMentorIdAndEndTimeAfter(eq(mentorId), any(), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "startTime"));
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getMeetingUrl()).isNotNull();
    }

    // ===================== updateSlot =====================

    @Test
    @DisplayName("Đóng slot chưa có người đặt: đổi giờ, tính lại giá, trạng thái cancelled")
    void updateSlot_close() {
        MentorSchedule schedule = slot(ScheduleStatus.available);
        when(mentorScheduleRepository.findWithLockByScheduleId(scheduleId)).thenReturn(Optional.of(schedule));
        when(mentorService.getMentorById(mentorId)).thenReturn(mentor(new BigDecimal("200000")));
        when(mentorScheduleRepository.save(any(MentorSchedule.class))).thenAnswer(inv -> inv.getArgument(0));

        MentorScheduleResponse response = mentorScheduleService.updateSlot(
                mentorId, scheduleId, updateRequest(ScheduleStatus.cancelled));

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.cancelled);
        assertThat(schedule.getPrice()).isEqualByComparingTo(new BigDecimal("400000.00"));
        assertThat(schedule.getMeetingUrl()).isEqualTo("https://meet.google.com/new-link");
        assertThat(response.getStatus()).isEqualTo("cancelled");
        // Đóng slot thì không cần kiểm tra trùng giờ
        verify(mentorScheduleRepository, never())
                .existsByMentorIdAndScheduleIdNotAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                        any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Mở lại slot bị trùng giờ với slot khác -> SCHEDULE_TIME_CONFLICT")
    void updateSlot_reopenOverlap() {
        when(mentorScheduleRepository.findWithLockByScheduleId(scheduleId))
                .thenReturn(Optional.of(slot(ScheduleStatus.cancelled)));
        when(mentorScheduleRepository.existsByMentorIdAndScheduleIdNotAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                eq(mentorId), eq(scheduleId), eq(ScheduleStatus.cancelled), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> mentorScheduleService.updateSlot(
                mentorId, scheduleId, updateRequest(ScheduleStatus.available)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_TIME_CONFLICT);
        verify(mentorScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa slot đã có người đặt -> SCHEDULE_HAS_BOOKING")
    void updateSlot_booked() {
        when(mentorScheduleRepository.findWithLockByScheduleId(scheduleId))
                .thenReturn(Optional.of(slot(ScheduleStatus.booked)));

        assertThatThrownBy(() -> mentorScheduleService.updateSlot(
                mentorId, scheduleId, updateRequest(ScheduleStatus.available)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_HAS_BOOKING);
        verify(mentorScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chuyển slot sang booked bằng API sửa -> SCHEDULE_STATUS_INVALID")
    void updateSlot_invalidStatus() {
        assertThatThrownBy(() -> mentorScheduleService.updateSlot(
                mentorId, scheduleId, updateRequest(ScheduleStatus.booked)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_STATUS_INVALID);
        verify(mentorScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sửa slot của mentor khác -> SCHEDULE_NOT_FOUND")
    void updateSlot_otherMentor() {
        when(mentorScheduleRepository.findWithLockByScheduleId(scheduleId))
                .thenReturn(Optional.of(slot(ScheduleStatus.available)));

        assertThatThrownBy(() -> mentorScheduleService.updateSlot(
                UUID.randomUUID(), scheduleId, updateRequest(ScheduleStatus.available)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_NOT_FOUND);
    }

    // ===================== deleteSlot =====================

    @Test
    @DisplayName("Xóa slot còn booking chưa hủy -> SCHEDULE_HAS_BOOKING")
    void deleteSlot_hasBooking() {
        when(mentorScheduleRepository.findWithLockByScheduleId(scheduleId))
                .thenReturn(Optional.of(slot(ScheduleStatus.booked)));
        when(bookingRepository.existsBySchedule_ScheduleIdAndStatusNot(scheduleId, BookingStatus.cancelled))
                .thenReturn(true);

        assertThatThrownBy(() -> mentorScheduleService.deleteSlot(mentorId, scheduleId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_HAS_BOOKING);
        verify(mentorScheduleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Xóa slot chưa có booking -> xóa thành công")
    void deleteSlot_success() {
        MentorSchedule schedule = slot(ScheduleStatus.available);
        when(mentorScheduleRepository.findWithLockByScheduleId(scheduleId)).thenReturn(Optional.of(schedule));
        when(bookingRepository.existsBySchedule_ScheduleIdAndStatusNot(scheduleId, BookingStatus.cancelled))
                .thenReturn(false);

        mentorScheduleService.deleteSlot(mentorId, scheduleId);

        verify(mentorScheduleRepository).delete(schedule);
    }

    // ===================== getAvailability =====================

    @Test
    @DisplayName("Học viên xem lịch: thấy slot trống + đã đặt, ẩn meeting link, mặc định 7 ngày")
    void getAvailability_hideMeetingUrl() {
        when(mentorScheduleRepository
                .findByMentorIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        eq(mentorId), anyCollection(), any(), any()))
                .thenReturn(List.of(slot(ScheduleStatus.available), slot(ScheduleStatus.booked)));

        List<MentorScheduleResponse> result = mentorScheduleService.getAvailability(mentorId, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(r -> assertThat(r.getMeetingUrl()).isNull());

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mentorScheduleRepository)
                .findByMentorIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        eq(mentorId), eq(List.of(ScheduleStatus.available, ScheduleStatus.booked)),
                        fromCaptor.capture(), toCaptor.capture());
        assertThat(Duration.between(fromCaptor.getValue(), toCaptor.getValue())).isEqualTo(Duration.ofDays(7));
        verify(mentorService).getMentorById(mentorId);
    }

    @Test
    @DisplayName("from ở quá khứ thì chỉ lấy từ thời điểm hiện tại")
    void getAvailability_fromInPast() {
        Instant past = Instant.now().minus(3, ChronoUnit.DAYS);

        mentorScheduleService.getAvailability(mentorId, past, null);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mentorScheduleRepository)
                .findByMentorIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        eq(mentorId), anyCollection(), fromCaptor.capture(), any());
        assertThat(fromCaptor.getValue()).isAfter(UtcTimeUtil.toUtc(past));
    }
}
