package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.CreateMentorScheduleRequest;
import com.sba301.englishcenter.dto.request.UpdateMentorScheduleRequest;
import com.sba301.englishcenter.dto.response.MentorScheduleResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MentorScheduleService {

    /**
     * Mentor mở 1 slot thời gian rảnh. Giá = hourly_rate x số giờ của slot (TASK D5-02).
     */
    MentorScheduleResponse createSlot(UUID mentorId, CreateMentorScheduleRequest request);

    /**
     * Mentor xem các slot chưa kết thúc của chính mình (mọi trạng thái).
     */
    PageResponse<MentorScheduleResponse> getMySlots(UUID mentorId, Pageable pageable);

    /**
     * Mentor sửa giờ / link, đóng hoặc mở lại 1 slot chưa được đặt (TASK D5-03).
     */
    MentorScheduleResponse updateSlot(UUID mentorId, UUID scheduleId, UpdateMentorScheduleRequest request);

    /**
     * Mentor xóa slot. Không cho xóa slot đã có booking chưa hủy (TASK D5-04).
     */
    void deleteSlot(UUID mentorId, UUID scheduleId);

    /**
     * Xem lịch của 1 mentor trong khoảng [from, to): slot còn trống và slot đã có người đặt, chưa tới giờ (TASK D5-05).
     */
    List<MentorScheduleResponse> getAvailability(UUID mentorId, Instant from, Instant to);
}
