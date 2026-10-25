package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Slot của mentor. startTime / endTime là thời điểm UTC (ISO-8601 có "Z").
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorScheduleResponse {

    private UUID scheduleId;
    private UUID mentorId;
    private Instant startTime;
    private Instant endTime;
    private String status;
    private BigDecimal price;
    private String meetingUrl;
}
