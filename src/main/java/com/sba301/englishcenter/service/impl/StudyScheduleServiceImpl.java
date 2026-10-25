package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.common.enums.RepeatRule;
import com.sba301.englishcenter.common.enums.StudyStatus;
import com.sba301.englishcenter.dto.request.StudyScheduleRequest;
import com.sba301.englishcenter.dto.request.UpdateStudyStatusRequest;
import com.sba301.englishcenter.dto.response.StudyScheduleResponse;
import com.sba301.englishcenter.entity.StudySchedule;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.StudyScheduleRepository;
import com.sba301.englishcenter.service.CourseService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.StudyScheduleService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudyScheduleServiceImpl implements StudyScheduleService {

    private static final int DEFAULT_REMINDER_MINUTES = 15;
    // Bằng giới hạn @Max của request: job chỉ cần xét lịch trong 1 ngày tới
    private static final int MAX_REMINDER_MINUTES = 1440;
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final Sort SORT_BY_TIME = Sort.by(Sort.Direction.ASC, "scheduledTime");

    private final StudyScheduleRepository studyScheduleRepository;
    // Domain khác chỉ gọi qua interface
    private final CourseService courseService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudyScheduleResponse> getMySchedules(UUID studentId, Instant from, Instant to,
                                                              StudyStatus status, Pageable pageable) {
        // Mặc định: từ đầu hôm nay (UTC) tới 30 ngày sau
        LocalDateTime fromTime = from != null ? UtcTimeUtil.toUtc(from) : UtcTimeUtil.nowUtc().toLocalDate().atStartOfDay();
        LocalDateTime toTime = to != null ? UtcTimeUtil.toUtc(to) : fromTime.plusDays(DEFAULT_RANGE_DAYS);
        if (!toTime.isAfter(fromTime)) {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_TIME);
        }

        // Luôn sắp theo giờ học tăng dần, bỏ qua sort client gửi lên
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), SORT_BY_TIME);
        Page<StudySchedule> page = status == null
                ? studyScheduleRepository.findByStudentIdAndScheduledTimeGreaterThanEqualAndScheduledTimeLessThan(
                        studentId, fromTime, toTime, sorted)
                : studyScheduleRepository.findByStudentIdAndStatusAndScheduledTimeGreaterThanEqualAndScheduledTimeLessThan(
                        studentId, status, fromTime, toTime, sorted);

        return PageResponse.<StudyScheduleResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(this::toResponse).toList())
                .build();
    }

    @Override
    @Transactional
    public StudyScheduleResponse create(UUID studentId, StudyScheduleRequest request) {
        LocalDateTime scheduledTime = requireFuture(request.getScheduledTime());
        validateLesson(request.getLessonContentId());

        StudySchedule schedule = studyScheduleRepository.save(StudySchedule.builder()
                .studentId(studentId)
                .title(request.getTitle().trim())
                .lessonContentId(request.getLessonContentId())
                .scheduledTime(scheduledTime)
                .repeatRule(request.getRepeatRule() != null ? request.getRepeatRule() : RepeatRule.none)
                .reminderMinutesBefore(reminderMinutes(request))
                .status(StudyStatus.pending)
                .createdAt(UtcTimeUtil.nowUtc())
                .build());
        log.info("Student {} created study schedule {}", studentId, schedule.getScheduleId());
        return toResponse(schedule);
    }

    @Override
    @Transactional
    public StudyScheduleResponse update(UUID studentId, UUID scheduleId, StudyScheduleRequest request) {
        StudySchedule schedule = findOwned(studentId, scheduleId);
        LocalDateTime scheduledTime = UtcTimeUtil.toUtc(request.getScheduledTime());
        int reminderMinutes = reminderMinutes(request);

        // Đổi giờ học thì giờ mới phải ở tương lai
        if (!scheduledTime.equals(schedule.getScheduledTime())) {
            requireFuture(request.getScheduledTime());
        }
        validateLesson(request.getLessonContentId());

        // Đổi giờ / số phút nhắc -> được nhắc lại cho lần học mới
        if (!scheduledTime.equals(schedule.getScheduledTime())
                || reminderMinutes != schedule.getReminderMinutesBefore()) {
            schedule.setReminderSentAt(null);
        }
        schedule.setTitle(request.getTitle().trim());
        schedule.setLessonContentId(request.getLessonContentId());
        schedule.setScheduledTime(scheduledTime);
        schedule.setRepeatRule(request.getRepeatRule() != null ? request.getRepeatRule() : RepeatRule.none);
        schedule.setReminderMinutesBefore(reminderMinutes);
        return toResponse(studyScheduleRepository.save(schedule));
    }

    @Override
    @Transactional
    public StudyScheduleResponse updateStatus(UUID studentId, UUID scheduleId, UpdateStudyStatusRequest request) {
        StudySchedule schedule = findOwned(studentId, scheduleId);
        schedule.setStatus(request.getStatus());
        // Mở lại lịch còn ở tương lai thì cho phép nhắc lại
        if (request.getStatus() == StudyStatus.pending && schedule.getScheduledTime().isAfter(UtcTimeUtil.nowUtc())) {
            schedule.setReminderSentAt(null);
        }
        return toResponse(studyScheduleRepository.save(schedule));
    }

    @Override
    @Transactional
    public void delete(UUID studentId, UUID scheduleId) {
        studyScheduleRepository.delete(findOwned(studentId, scheduleId));
        log.info("Student {} deleted study schedule {}", studentId, scheduleId);
    }

    @Override
    @Transactional
    public int sendDueReminders() {
        LocalDateTime now = UtcTimeUtil.nowUtc();

        // 1. Lịch lặp lại (daily/weekly) đã qua giờ -> chuyển sang lần kế tiếp, nhắc lại từ đầu
        for (StudySchedule schedule : studyScheduleRepository
                .findByStatusAndRepeatRuleNotAndScheduledTimeLessThanEqual(StudyStatus.pending, RepeatRule.none, now)) {
            schedule.setScheduledTime(nextOccurrence(schedule.getScheduledTime(), schedule.getRepeatRule(), now));
            schedule.setReminderSentAt(null);
            studyScheduleRepository.save(schedule);
        }

        // 2. Lịch đang chờ, chưa nhắc, đã tới giờ nhắc (scheduled_time - reminder_minutes_before <= now)
        List<StudySchedule> due = studyScheduleRepository
                .findByStatusAndReminderSentAtIsNullAndScheduledTimeGreaterThanAndScheduledTimeLessThanEqual(
                        StudyStatus.pending, now, now.plusMinutes(MAX_REMINDER_MINUTES))
                .stream()
                .filter(s -> !s.getScheduledTime().minusMinutes(reminderOf(s)).isAfter(now))
                .toList();

        for (StudySchedule schedule : due) {
            long minutesLeft = Math.max(1, Duration.between(now, schedule.getScheduledTime()).toMinutes());
            notificationService.notify(schedule.getStudentId(), NotificationType.study_reminder, "Nhắc nhở học tập",
                    "Bạn có lịch học \"" + schedule.getTitle() + "\" sau khoảng " + minutesLeft + " phút.");
            schedule.setReminderSentAt(now);
            studyScheduleRepository.save(schedule);
        }
        if (!due.isEmpty()) {
            log.info("Sent study reminders for {} schedule(s)", due.size());
        }
        return due.size();
    }

    // ===================== helpers =====================

    private StudySchedule findOwned(UUID studentId, UUID scheduleId) {
        // Lịch của người khác -> coi như không tồn tại
        return studyScheduleRepository.findByScheduleIdAndStudentId(scheduleId, studentId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDY_SCHEDULE_NOT_FOUND));
    }

    private LocalDateTime requireFuture(Instant time) {
        LocalDateTime utc = UtcTimeUtil.toUtc(time);
        if (!utc.isAfter(UtcTimeUtil.nowUtc())) {
            throw new AppException(ErrorCode.STUDY_SCHEDULE_TIME_INVALID);
        }
        return utc;
    }

    private void validateLesson(UUID lessonContentId) {
        if (lessonContentId != null && !courseService.existsContent(lessonContentId)) {
            throw new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND);
        }
    }

    private int reminderMinutes(StudyScheduleRequest request) {
        return request.getReminderMinutesBefore() != null ? request.getReminderMinutesBefore() : DEFAULT_REMINDER_MINUTES;
    }

    private int reminderOf(StudySchedule schedule) {
        return schedule.getReminderMinutesBefore() != null ? schedule.getReminderMinutesBefore() : DEFAULT_REMINDER_MINUTES;
    }

    // Lần học kế tiếp sau thời điểm now
    private LocalDateTime nextOccurrence(LocalDateTime time, RepeatRule rule, LocalDateTime now) {
        LocalDateTime next = time;
        while (!next.isAfter(now)) {
            next = rule == RepeatRule.weekly ? next.plusWeeks(1) : next.plusDays(1);
        }
        return next;
    }

    private StudyScheduleResponse toResponse(StudySchedule schedule) {
        return StudyScheduleResponse.builder()
                .scheduleId(schedule.getScheduleId())
                .title(schedule.getTitle())
                .lessonContentId(schedule.getLessonContentId())
                .scheduledTime(UtcTimeUtil.toInstant(schedule.getScheduledTime()))
                .repeatRule(schedule.getRepeatRule() != null ? schedule.getRepeatRule().name() : null)
                .reminderMinutesBefore(schedule.getReminderMinutesBefore())
                .status(schedule.getStatus() != null ? schedule.getStatus().name() : null)
                .reminderSentAt(UtcTimeUtil.toInstant(schedule.getReminderSentAt()))
                .createdAt(UtcTimeUtil.toInstant(schedule.getCreatedAt()))
                .build();
    }
}
