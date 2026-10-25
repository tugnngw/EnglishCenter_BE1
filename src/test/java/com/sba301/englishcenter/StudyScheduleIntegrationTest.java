package com.sba301.englishcenter;

import com.sba301.englishcenter.common.enums.RepeatRule;
import com.sba301.englishcenter.common.enums.StudyStatus;
import com.sba301.englishcenter.dto.request.StudyScheduleRequest;
import com.sba301.englishcenter.dto.request.UpdateStudyStatusRequest;
import com.sba301.englishcenter.service.StudyScheduleService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-D5-01: lịch học cá nhân trên PostgreSQL thật (enum repeat_rule/study_status, UTC, nhắc lịch).
 */
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.datasource.url=${PAYMENT_TEST_DB_URL}", "spring.datasource.username=postgres",
        "spring.datasource.password=dev4-test", "spring.docker.compose.enabled=false",
        "spring.jpa.show-sql=false", "logging.level.org.flywaydb=INFO",
        "jwt.signer-key=0123456789012345678901234567890123456789012345678901234567890123",
        "app.study-schedule.reminder-job.enabled=false", "app.booking.reminder-job.enabled=false"
})
class StudyScheduleIntegrationTest {
    @Autowired StudyScheduleService schedules;
    @Autowired JdbcTemplate jdbc;
    UUID studentId;

    @BeforeEach void fixture() {
        studentId = UUID.randomUUID();
        jdbc.update("insert into users(user_id,email,password,full_name,role_id) values (?,?,?,'Study student',(select role_id from roles where role_name='STUDENT'))",
                studentId, studentId + "@test.invalid", "not-a-login-password");
    }

    StudyScheduleRequest request(Instant time, RepeatRule rule, int reminder) {
        var r = new StudyScheduleRequest();
        r.setTitle("Học Lesson 5: Grammar");
        r.setScheduledTime(time);
        r.setRepeatRule(rule);
        r.setReminderMinutesBefore(reminder);
        return r;
    }
    int reminders() {
        return jdbc.queryForObject("select count(*) from notifications where user_id=? and type='study_reminder'", Integer.class, studentId);
    }

    @Test void crudKeepsUtcAndEnums() {
        Instant time = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        var created = schedules.create(studentId, request(time, RepeatRule.weekly, 30));
        assertEquals(time, created.getScheduledTime());
        assertEquals("weekly", created.getRepeatRule());
        assertEquals("weekly", jdbc.queryForObject("select repeat_rule::text from study_schedules where schedule_id=?", String.class, created.getScheduleId()));

        var page = schedules.getMySchedules(studentId, null, null, null, PageRequest.of(0, 20));
        assertEquals(1, page.getTotalElements());

        var status = new UpdateStudyStatusRequest();
        status.setStatus(StudyStatus.done);
        assertEquals("done", schedules.updateStatus(studentId, created.getScheduleId(), status).getStatus());
        assertEquals(0, schedules.getMySchedules(studentId, null, null, StudyStatus.pending, PageRequest.of(0, 20)).getTotalElements());

        // Người khác không thao tác được lịch này
        assertThrows(Exception.class, () -> schedules.delete(UUID.randomUUID(), created.getScheduleId()));
        schedules.delete(studentId, created.getScheduleId());
        assertEquals(0, schedules.getMySchedules(studentId, null, null, null, PageRequest.of(0, 20)).getTotalElements());
    }

    @Test void dueScheduleIsRemindedOnceAndFutureOneWaits() {
        var soon = schedules.create(studentId, request(Instant.now().plus(10, ChronoUnit.MINUTES), RepeatRule.none, 15));
        var later = schedules.create(studentId, request(Instant.now().plus(3, ChronoUnit.HOURS), RepeatRule.none, 15));

        schedules.sendDueReminders();
        schedules.sendDueReminders();

        assertEquals(1, reminders());
        assertNotNull(jdbc.queryForObject("select reminder_sent_at from study_schedules where schedule_id=?", Timestamp.class, soon.getScheduleId()));
        assertNull(jdbc.queryForObject("select reminder_sent_at from study_schedules where schedule_id=?", Timestamp.class, later.getScheduleId()));
    }

    @Test void passedWeeklyScheduleMovesToNextWeek() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into study_schedules(schedule_id,student_id,title,scheduled_time,repeat_rule,reminder_minutes_before,status,reminder_sent_at)
                values (?,?,'Weekly',(now() at time zone 'utc') - interval '2 days','weekly',15,'pending',(now() at time zone 'utc') - interval '2 days')
                """, id, studentId);

        schedules.sendDueReminders();

        Boolean movedForward = jdbc.queryForObject(
                "select scheduled_time > (now() at time zone 'utc') + interval '4 days' and reminder_sent_at is null from study_schedules where schedule_id=?",
                Boolean.class, id);
        assertTrue(movedForward);
    }
}
