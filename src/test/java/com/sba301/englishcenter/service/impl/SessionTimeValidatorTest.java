package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.util.UtcTimeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Dev 5: validate giờ buổi kèm 1-1 (chặn đặt lịch quá khứ, năm 3000, buổi quá ngắn / quá dài).
 */
class SessionTimeValidatorTest {

    private SessionTimeValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SessionTimeValidator();
        ReflectionTestUtils.setField(validator, "maxDaysAhead", 90L);
        ReflectionTestUtils.setField(validator, "minSessionMinutes", 30L);
        ReflectionTestUtils.setField(validator, "maxSessionMinutes", 180L);
    }

    private ErrorCode errorOf(LocalDateTime start, LocalDateTime end) {
        return assertThrows(AppException.class, () -> validator.validate(start, end)).getErrorCode();
    }

    @Test
    void validSession() {
        LocalDateTime start = UtcTimeUtil.nowUtc().plusDays(2);
        assertDoesNotThrow(() -> validator.validate(start, start.plusMinutes(60)));
    }

    @Test
    void year3000IsRejected() {
        LocalDateTime start = LocalDateTime.of(3000, 1, 1, 12, 0);
        assertEquals(ErrorCode.SCHEDULE_TOO_FAR, errorOf(start, start.plusHours(1)));
    }

    @Test
    void pastIsRejected() {
        LocalDateTime start = UtcTimeUtil.nowUtc().minusHours(1);
        assertEquals(ErrorCode.SCHEDULE_IN_PAST, errorOf(start, start.plusHours(1)));
    }

    @Test
    void endBeforeStartIsRejected() {
        LocalDateTime start = UtcTimeUtil.nowUtc().plusDays(1);
        assertEquals(ErrorCode.INVALID_SCHEDULE_TIME, errorOf(start, start.minusMinutes(10)));
    }

    @Test
    void tooShortOrTooLongIsRejected() {
        LocalDateTime start = UtcTimeUtil.nowUtc().plusDays(1);
        assertEquals(ErrorCode.SCHEDULE_DURATION_INVALID, errorOf(start, start.plusMinutes(15)));
        assertEquals(ErrorCode.SCHEDULE_DURATION_INVALID, errorOf(start, start.plusHours(5)));
    }
}
