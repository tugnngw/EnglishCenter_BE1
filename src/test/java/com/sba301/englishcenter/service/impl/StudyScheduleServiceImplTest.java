package com.sba301.englishcenter.service.impl;

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
import com.sba301.englishcenter.util.UtcTimeUtil;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyScheduleServiceImplTest {

    @Mock
    private StudyScheduleRepository studyScheduleRepository;

    @Mock
    private CourseService courseService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private StudyScheduleServiceImpl studyScheduleService;

    private final UUID studentId = UUID.randomUUID();
    private final UUID scheduleId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(studyScheduleRepository.save(any(StudySchedule.class))).thenAnswer(inv -> {
            StudySchedule s = inv.getArgument(0);
            if (s.getScheduleId() == null) {
                s.setScheduleId(UUID.randomUUID());
            }
            return s;
        });
    }

    private StudyScheduleRequest request(Instant time) {
        StudyScheduleRequest request = new StudyScheduleRequest();
        request.setTitle("  Học Lesson 5: Grammar  ");
        request.setScheduledTime(time);
        return request;
    }

    private StudySchedule schedule(LocalDateTime time, RepeatRule rule, int reminder) {
        return StudySchedule.builder()
                .scheduleId(scheduleId).studentId(studentId).title("Học Lesson 5")
                .scheduledTime(time).repeatRule(rule).reminderMinutesBefore(reminder)
                .status(StudyStatus.pending).build();
    }

    // ===================== create / update =====================

    @Test
    @DisplayName("Tạo lịch: lưu UTC, mặc định không lặp + nhắc trước 15 phút, trạng thái pending")
    void create_defaults() {
        Instant time = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        StudyScheduleResponse response = studyScheduleService.create(studentId, request(time));

        ArgumentCaptor<StudySchedule> captor = ArgumentCaptor.forClass(StudySchedule.class);
        verify(studyScheduleRepository).save(captor.capture());
        StudySchedule saved = captor.getValue();
        assertThat(saved.getStudentId()).isEqualTo(studentId);
        assertThat(saved.getTitle()).isEqualTo("Học Lesson 5: Grammar");
        assertThat(saved.getScheduledTime()).isEqualTo(UtcTimeUtil.toUtc(time));
        assertThat(saved.getRepeatRule()).isEqualTo(RepeatRule.none);
        assertThat(saved.getReminderMinutesBefore()).isEqualTo(15);
        assertThat(saved.getStatus()).isEqualTo(StudyStatus.pending);
        assertThat(response.getScheduledTime()).isEqualTo(time);
    }

    @Test
    @DisplayName("Tạo lịch ở quá khứ -> STUDY_SCHEDULE_TIME_INVALID")
    void create_pastTime() {
        assertThatThrownBy(() -> studyScheduleService.create(studentId, request(Instant.now().minusSeconds(60))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STUDY_SCHEDULE_TIME_INVALID);
        verify(studyScheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gắn bài học không tồn tại -> COURSE_CONTENT_NOT_FOUND")
    void create_unknownLesson() {
        StudyScheduleRequest request = request(Instant.now().plus(1, ChronoUnit.DAYS));
        UUID lessonId = UUID.randomUUID();
        request.setLessonContentId(lessonId);
        when(courseService.existsContent(lessonId)).thenReturn(false);

        assertThatThrownBy(() -> studyScheduleService.create(studentId, request))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_CONTENT_NOT_FOUND);
    }

    @Test
    @DisplayName("Sửa lịch của người khác -> STUDY_SCHEDULE_NOT_FOUND")
    void update_notOwner() {
        when(studyScheduleRepository.findByScheduleIdAndStudentId(scheduleId, studentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> studyScheduleService.update(studentId, scheduleId, request(Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STUDY_SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("Đổi giờ học -> được nhắc lại cho giờ mới")
    void update_newTimeResetsReminder() {
        StudySchedule existing = schedule(UtcTimeUtil.nowUtc().plusHours(5), RepeatRule.none, 15);
        existing.setReminderSentAt(UtcTimeUtil.nowUtc());
        when(studyScheduleRepository.findByScheduleIdAndStudentId(scheduleId, studentId)).thenReturn(Optional.of(existing));

        studyScheduleService.update(studentId, scheduleId, request(Instant.now().plus(2, ChronoUnit.DAYS)));

        assertThat(existing.getReminderSentAt()).isNull();
    }

    @Test
    @DisplayName("Đánh dấu đã học (done)")
    void updateStatus_done() {
        StudySchedule existing = schedule(UtcTimeUtil.nowUtc().minusHours(1), RepeatRule.none, 15);
        when(studyScheduleRepository.findByScheduleIdAndStudentId(scheduleId, studentId)).thenReturn(Optional.of(existing));
        UpdateStudyStatusRequest request = new UpdateStudyStatusRequest();
        request.setStatus(StudyStatus.done);

        assertThat(studyScheduleService.updateStatus(studentId, scheduleId, request).getStatus()).isEqualTo("done");
    }

    @Test
    @DisplayName("Danh sách: khoảng thời gian sai (to <= from) -> INVALID_SCHEDULE_TIME")
    void getMySchedules_invalidRange() {
        Instant from = Instant.now();
        assertThatThrownBy(() -> studyScheduleService.getMySchedules(studentId, from, from, null, PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_SCHEDULE_TIME);
    }

    @Test
    @DisplayName("Danh sách: chỉ lấy lịch của chính mình, luôn sắp theo giờ học")
    void getMySchedules_ownOnly() {
        Page<StudySchedule> page = new PageImpl<>(List.of(schedule(UtcTimeUtil.nowUtc().plusHours(3), RepeatRule.none, 15)));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(studyScheduleRepository.findByStudentIdAndScheduledTimeGreaterThanEqualAndScheduledTimeLessThan(
                eq(studentId), any(), any(), pageable.capture())).thenReturn(page);

        assertThat(studyScheduleService.getMySchedules(studentId, null, null, null, PageRequest.of(0, 20)).getData()).hasSize(1);
        assertThat(pageable.getValue().getSort().getOrderFor("scheduledTime")).isNotNull();
    }

    // ===================== nhắc lịch =====================

    @Test
    @DisplayName("Nhắc: tới giờ nhắc thì gửi study_reminder 1 lần; chưa tới giờ nhắc thì chưa gửi")
    void sendDueReminders_onlyDue() {
        StudySchedule due = schedule(UtcTimeUtil.nowUtc().plusMinutes(10), RepeatRule.none, 15);
        StudySchedule notYet = schedule(UtcTimeUtil.nowUtc().plusMinutes(50), RepeatRule.none, 15);
        when(studyScheduleRepository.findByStatusAndRepeatRuleNotAndScheduledTimeLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());
        when(studyScheduleRepository.findByStatusAndReminderSentAtIsNullAndScheduledTimeGreaterThanAndScheduledTimeLessThanEqual(
                eq(StudyStatus.pending), any(), any())).thenReturn(List.of(due, notYet));

        assertThat(studyScheduleService.sendDueReminders()).isEqualTo(1);
        assertThat(due.getReminderSentAt()).isNotNull();
        assertThat(notYet.getReminderSentAt()).isNull();
        verify(notificationService).notify(eq(studentId), eq(NotificationType.study_reminder), eq("Nhắc nhở học tập"), anyString());
    }

    @Test
    @DisplayName("Lặp lại weekly đã qua giờ -> chuyển sang tuần kế tiếp và cho nhắc lại")
    void sendDueReminders_rollsRepeating() {
        LocalDateTime past = UtcTimeUtil.nowUtc().minusDays(8);
        StudySchedule weekly = schedule(past, RepeatRule.weekly, 15);
        weekly.setReminderSentAt(past.minusMinutes(15));
        when(studyScheduleRepository.findByStatusAndRepeatRuleNotAndScheduledTimeLessThanEqual(
                eq(StudyStatus.pending), eq(RepeatRule.none), any())).thenReturn(List.of(weekly));
        when(studyScheduleRepository.findByStatusAndReminderSentAtIsNullAndScheduledTimeGreaterThanAndScheduledTimeLessThanEqual(
                any(), any(), any())).thenReturn(List.of());

        studyScheduleService.sendDueReminders();

        assertThat(weekly.getScheduledTime()).isEqualTo(past.plusWeeks(2));
        assertThat(weekly.getScheduledTime()).isAfter(UtcTimeUtil.nowUtc());
        assertThat(weekly.getReminderSentAt()).isNull();
    }
}
