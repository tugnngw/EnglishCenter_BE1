package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.service.StudyScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job nhắc lịch học cá nhân (TASK-D5-01, USER_FLOWS 1.8 "Cronjob - Check schedules" mỗi phút).
 * Tắt bằng app.study-schedule.reminder-job.enabled=false.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.study-schedule.reminder-job.enabled", havingValue = "true", matchIfMissing = true)
public class StudyReminderJob {

    private final StudyScheduleService studyScheduleService;

    @Scheduled(fixedDelayString = "${app.study-schedule.reminder-job.fixed-delay-ms:60000}")
    public void sendReminders() {
        studyScheduleService.sendDueReminders();
    }
}
