package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.StudyStatus;
import com.sba301.englishcenter.dto.request.StudyScheduleRequest;
import com.sba301.englishcenter.dto.request.UpdateStudyStatusRequest;
import com.sba301.englishcenter.dto.response.StudyScheduleResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

/**
 * Lịch nhắc học tập cá nhân của học viên (TASK-D5-01, USER_FLOWS 1.8). Mọi thao tác chỉ trên lịch của chính mình.
 */
public interface StudyScheduleService {

    /** Lịch trong khoảng [from, to) (mặc định from = đầu hôm nay UTC, to = from + 30 ngày), lọc trạng thái nếu có. */
    PageResponse<StudyScheduleResponse> getMySchedules(UUID studentId, Instant from, Instant to,
                                                       StudyStatus status, Pageable pageable);

    StudyScheduleResponse create(UUID studentId, StudyScheduleRequest request);

    StudyScheduleResponse update(UUID studentId, UUID scheduleId, StudyScheduleRequest request);

    StudyScheduleResponse updateStatus(UUID studentId, UUID scheduleId, UpdateStudyStatusRequest request);

    void delete(UUID studentId, UUID scheduleId);

    /**
     * Job mỗi phút: chuyển lịch lặp lại đã qua sang lần kế tiếp, rồi gửi thông báo study_reminder
     * cho lịch đang chờ tới giờ nhắc (scheduled_time - reminder_minutes_before). Mỗi lần học chỉ nhắc 1 lần.
     *
     * @return số lịch vừa được nhắc
     */
    int sendDueReminders();
}
