package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.util.UtcTimeUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Kiểm tra giờ của 1 buổi kèm 1-1 (giờ UTC):
 * <ol>
 *     <li>giờ kết thúc phải sau giờ bắt đầu;</li>
 *     <li>giờ bắt đầu phải ở tương lai;</li>
 *     <li>chỉ xếp trong {@code app.schedule.max-days-ahead} ngày tới (chặn kiểu đặt lịch năm 3000);</li>
 *     <li>mỗi buổi dài từ {@code min-session-minutes} tới {@code max-session-minutes} phút.</li>
 * </ol>
 */
@Component
public class SessionTimeValidator {

    @Value("${app.schedule.max-days-ahead:90}")
    private long maxDaysAhead;

    @Value("${app.schedule.min-session-minutes:30}")
    private long minSessionMinutes;

    @Value("${app.schedule.max-session-minutes:180}")
    private long maxSessionMinutes;

    public void validate(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_TIME);
        }
        LocalDateTime now = UtcTimeUtil.nowUtc();
        if (!startTime.isAfter(now)) {
            throw new AppException(ErrorCode.SCHEDULE_IN_PAST);
        }
        if (startTime.isAfter(now.plusDays(maxDaysAhead))) {
            throw new AppException(ErrorCode.SCHEDULE_TOO_FAR,
                    String.format(ErrorCode.SCHEDULE_TOO_FAR.getMessage(), maxDaysAhead));
        }
        long minutes = Duration.between(startTime, endTime).toMinutes();
        if (minutes < minSessionMinutes || minutes > maxSessionMinutes) {
            throw new AppException(ErrorCode.SCHEDULE_DURATION_INVALID,
                    String.format(ErrorCode.SCHEDULE_DURATION_INVALID.getMessage(), minSessionMinutes, maxSessionMinutes));
        }
    }

    public long getMaxDaysAhead() {
        return maxDaysAhead;
    }
}
