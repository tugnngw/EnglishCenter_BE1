package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Lịch học cá nhân. Các mốc thời gian là thời điểm UTC (ISO-8601 có "Z").
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyScheduleResponse {

    private UUID scheduleId;
    private String title;
    private UUID lessonContentId;
    private Instant scheduledTime;
    private String repeatRule;
    private Integer reminderMinutesBefore;
    private String status;
    private Instant reminderSentAt;
    private Instant createdAt;
}
