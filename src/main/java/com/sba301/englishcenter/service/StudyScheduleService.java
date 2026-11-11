package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.dto.response.BookingResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

/**
 * Lịch học của học viên (Study Schedule). Học viên không tự tạo lịch nữa:
 * dữ liệu lấy từ các buổi kèm 1-1 mentor xếp trong gói ({@link BookingService}).
 * Nhắc giờ học do job nhắc của booking đảm nhận (BookingReminderJob).
 */
public interface StudyScheduleService {

    /**
     * Các buổi kèm 1-1 bắt đầu trong [from, to) (mặc định from = đầu hôm nay UTC, to = from + 30 ngày),
     * sắp theo giờ học tăng dần; lọc trạng thái nếu có (null = mọi buổi đã thanh toán).
     */
    PageResponse<BookingResponse> getMySchedules(UUID studentId, Instant from, Instant to,
                                                 BookingStatus status, Pageable pageable);
}
