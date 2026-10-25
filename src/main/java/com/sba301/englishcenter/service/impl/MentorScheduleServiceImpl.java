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
import com.sba301.englishcenter.service.MentorScheduleService;
import com.sba301.englishcenter.service.MentorService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorScheduleServiceImpl implements MentorScheduleService {

    // Luôn sắp xếp theo giờ bắt đầu, bỏ qua sort client gửi lên để tránh lỗi field không tồn tại
    private static final Sort SORT_BY_START_TIME = Sort.by(Sort.Direction.ASC, "startTime");
    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
    // Không truyền "to" thì mặc định xem 7 ngày kể từ "from"
    private static final Duration DEFAULT_AVAILABILITY_RANGE = Duration.ofDays(7);
    // Học viên thấy cả slot trống và slot đã có người đặt (FE tô xám), không thấy slot mentor đã đóng
    private static final List<ScheduleStatus> VISIBLE_STATUSES = List.of(ScheduleStatus.available, ScheduleStatus.booked);

    private final MentorScheduleRepository mentorScheduleRepository;
    private final BookingRepository bookingRepository;
    // Hồ sơ mentor thuộc domain Dev 1 -> chỉ gọi qua service interface
    private final MentorService mentorService;

    @Override
    @Transactional
    public MentorScheduleResponse createSlot(UUID mentorId, CreateMentorScheduleRequest request) {
        LocalDateTime startTime = UtcTimeUtil.toUtc(request.getStartTime());
        LocalDateTime endTime = UtcTimeUtil.toUtc(request.getEndTime());

        // 1. Giờ kết thúc phải sau giờ bắt đầu
        validateTimeRange(startTime, endTime);

        // 2. Lấy hồ sơ mentor (Admin tạo, có hourly_rate). Mentor bị khóa/thu hồi -> MENTOR_PROFILE_NOT_FOUND
        BigDecimal hourlyRate = getHourlyRate(mentorId);

        // 3. Không được trùng giờ với slot khác (chưa đóng) của chính mentor này (TASK D5-08)
        boolean isOverlap = mentorScheduleRepository
                .existsByMentorIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                        mentorId, ScheduleStatus.cancelled, endTime, startTime);
        if (isOverlap) {
            throw new AppException(ErrorCode.SCHEDULE_TIME_CONFLICT);
        }

        // 4. Tạo slot mới, giá = hourly_rate x số giờ, trạng thái "available"
        MentorSchedule schedule = MentorSchedule.builder()
                .mentorId(mentorId)
                .startTime(startTime)
                .endTime(endTime)
                .price(calculatePrice(hourlyRate, startTime, endTime))
                .meetingUrl(request.getMeetingUrl())
                .status(ScheduleStatus.available)
                .build();

        MentorSchedule saved = mentorScheduleRepository.save(schedule);
        log.info("Mentor {} created schedule {}", mentorId, saved.getScheduleId());
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MentorScheduleResponse> getMySlots(UUID mentorId, Pageable pageable) {
        Page<MentorSchedule> page = mentorScheduleRepository.findByMentorIdAndEndTimeAfter(
                mentorId, UtcTimeUtil.nowUtc(), sortByStartTime(pageable));
        return PageResponse.<MentorScheduleResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(this::toResponse).toList())
                .build();
    }

    @Override
    @Transactional
    public MentorScheduleResponse updateSlot(UUID mentorId, UUID scheduleId, UpdateMentorScheduleRequest request) {
        // 1. Chỉ được chuyển sang mở (available) hoặc đóng (cancelled)
        if (request.getStatus() != ScheduleStatus.available && request.getStatus() != ScheduleStatus.cancelled) {
            throw new AppException(ErrorCode.SCHEDULE_STATUS_INVALID);
        }

        // 2. Slot phải thuộc mentor đang đăng nhập và chưa có người đặt
        MentorSchedule schedule = getOwnSlotWithLock(mentorId, scheduleId);
        if (schedule.getStatus() != ScheduleStatus.available && schedule.getStatus() != ScheduleStatus.cancelled) {
            throw new AppException(ErrorCode.SCHEDULE_HAS_BOOKING);
        }

        LocalDateTime startTime = UtcTimeUtil.toUtc(request.getStartTime());
        LocalDateTime endTime = UtcTimeUtil.toUtc(request.getEndTime());
        validateTimeRange(startTime, endTime);

        // 3. Slot mở thì không được trùng giờ với slot khác (chưa đóng) của mentor
        if (request.getStatus() == ScheduleStatus.available) {
            boolean isOverlap = mentorScheduleRepository
                    .existsByMentorIdAndScheduleIdNotAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                            mentorId, scheduleId, ScheduleStatus.cancelled, endTime, startTime);
            if (isOverlap) {
                throw new AppException(ErrorCode.SCHEDULE_TIME_CONFLICT);
            }
        }

        // 4. Đổi giờ thì tính lại giá theo hourly_rate hiện tại
        schedule.setStartTime(startTime);
        schedule.setEndTime(endTime);
        schedule.setPrice(calculatePrice(getHourlyRate(mentorId), startTime, endTime));
        schedule.setMeetingUrl(request.getMeetingUrl());
        schedule.setStatus(request.getStatus());

        MentorSchedule saved = mentorScheduleRepository.save(schedule);
        log.info("Mentor {} updated schedule {} -> {}", mentorId, scheduleId, saved.getStatus());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteSlot(UUID mentorId, UUID scheduleId) {
        MentorSchedule schedule = getOwnSlotWithLock(mentorId, scheduleId);

        // Còn booking chưa hủy (đang đặt / đã học / vắng mặt) thì không được xóa (TASK D5-04)
        if (bookingRepository.existsBySchedule_ScheduleIdAndStatusNot(scheduleId, BookingStatus.cancelled)) {
            throw new AppException(ErrorCode.SCHEDULE_HAS_BOOKING);
        }

        // Booking đã hủy của slot bị xóa theo (ON DELETE CASCADE trong V4)
        mentorScheduleRepository.delete(schedule);
        log.info("Mentor {} deleted schedule {}", mentorId, scheduleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorScheduleResponse> getAvailability(UUID mentorId, Instant from, Instant to) {
        // Mentor phải tồn tại và còn hoạt động (đã bị thu hồi/khóa thì không cho xem slot)
        mentorService.getMentorById(mentorId);

        // Không xem slot đã qua giờ bắt đầu
        LocalDateTime now = UtcTimeUtil.nowUtc();
        LocalDateTime fromTime = from == null ? now : UtcTimeUtil.toUtc(from);
        if (fromTime.isBefore(now)) {
            fromTime = now;
        }
        LocalDateTime toTime = to == null ? fromTime.plus(DEFAULT_AVAILABILITY_RANGE) : UtcTimeUtil.toUtc(to);

        // Học viên chỉ xem giờ và trạng thái -> ẩn meeting link
        return mentorScheduleRepository
                .findByMentorIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
                        mentorId, VISIBLE_STATUSES, fromTime, toTime)
                .stream()
                .map(this::toResponseWithoutMeetingUrl)
                .toList();
    }

    private MentorSchedule getOwnSlotWithLock(UUID mentorId, UUID scheduleId) {
        MentorSchedule schedule = mentorScheduleRepository.findWithLockByScheduleId(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));
        // Slot của mentor khác -> coi như không tồn tại
        if (!schedule.getMentorId().equals(mentorId)) {
            throw new AppException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        return schedule;
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_TIME);
        }
    }

    private BigDecimal getHourlyRate(UUID mentorId) {
        MentorResponse mentor = mentorService.getMentorById(mentorId);
        if (mentor.getHourlyRate() == null) {
            throw new AppException(ErrorCode.MENTOR_HOURLY_RATE_NOT_SET);
        }
        return mentor.getHourlyRate();
    }

    // Giá = hourly_rate x (số phút / 60), làm tròn tới đồng (SePay chỉ nhận số tiền nguyên VND), lưu NUMERIC(12,2)
    private BigDecimal calculatePrice(BigDecimal hourlyRate, LocalDateTime startTime, LocalDateTime endTime) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return hourlyRate.multiply(BigDecimal.valueOf(minutes))
                .divide(MINUTES_PER_HOUR, 0, RoundingMode.HALF_UP)
                .setScale(2);
    }

    private Pageable sortByStartTime(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), SORT_BY_START_TIME);
    }

    private MentorScheduleResponse toResponse(MentorSchedule schedule) {
        return MentorScheduleResponse.builder()
                .scheduleId(schedule.getScheduleId())
                .mentorId(schedule.getMentorId())
                .startTime(UtcTimeUtil.toInstant(schedule.getStartTime()))
                .endTime(UtcTimeUtil.toInstant(schedule.getEndTime()))
                .status(schedule.getStatus() != null ? schedule.getStatus().name() : null)
                .price(schedule.getPrice())
                .meetingUrl(schedule.getMeetingUrl())
                .build();
    }

    // Dùng cho lịch mà học viên xem: không trả meeting link
    private MentorScheduleResponse toResponseWithoutMeetingUrl(MentorSchedule schedule) {
        MentorScheduleResponse response = toResponse(schedule);
        response.setMeetingUrl(null);
        return response;
    }
}
