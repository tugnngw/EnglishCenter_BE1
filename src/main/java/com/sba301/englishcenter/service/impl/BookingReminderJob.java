package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job nhắc lịch học 1-1 (TASK-D5-02 "sắp đến giờ học" / D5-18).
 * Chạy mỗi phút; tắt bằng app.booking.reminder-job.enabled=false.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.booking.reminder-job.enabled", havingValue = "true", matchIfMissing = true)
public class BookingReminderJob {

    private final BookingService bookingService;

    @Scheduled(fixedDelayString = "${app.booking.reminder-job.fixed-delay-ms:60000}")
    public void sendReminders() {
        bookingService.sendUpcomingReminders();
    }
}
