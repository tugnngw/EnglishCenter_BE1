package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.service.BookingService;
import com.sba301.englishcenter.service.StudyScheduleService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudyScheduleServiceImpl implements StudyScheduleService {

    private static final int DEFAULT_RANGE_DAYS = 30;

    // Domain khác chỉ gọi qua interface
    private final BookingService bookingService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMySchedules(UUID studentId, Instant from, Instant to,
                                                        BookingStatus status, Pageable pageable) {
        // Mặc định: từ đầu hôm nay (UTC) tới 30 ngày sau
        LocalDateTime fromTime = from != null ? UtcTimeUtil.toUtc(from) : UtcTimeUtil.nowUtc().toLocalDate().atStartOfDay();
        LocalDateTime toTime = to != null ? UtcTimeUtil.toUtc(to) : fromTime.plusDays(DEFAULT_RANGE_DAYS);
        if (!toTime.isAfter(fromTime)) {
            throw new AppException(ErrorCode.INVALID_SCHEDULE_TIME);
        }
        return bookingService.getStudentSessions(studentId, fromTime, toTime, status, pageable);
    }
}
