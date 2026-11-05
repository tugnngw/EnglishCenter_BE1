package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.ConsultationStatus;
import com.sba301.englishcenter.dto.request.CreateConsultationRequest;
import com.sba301.englishcenter.dto.response.ConsultationResponse;
import com.sba301.englishcenter.dto.response.StudentOverviewResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Tư vấn lộ trình (Dev 5, V21).
 * Học viên làm test level xong để lại SĐT -> mọi mentor thấy yêu cầu -> mentor nhận trước liên hệ tư vấn
 * (qua SĐT / email trong hồ sơ) -> chốt thì tạo gói kèm 1-1 ({@link MentoringPlanService}).
 * Mentor nào cũng tìm được học viên để xem điểm test level.
 */
public interface ConsultationService {

    /** Học viên gửi (hoặc cập nhật yêu cầu đang mở) và lưu SĐT vào hồ sơ. Báo cho mọi mentor. */
    ConsultationResponse request(UUID studentId, CreateConsultationRequest request);

    /** Yêu cầu gần nhất của học viên, null nếu chưa gửi. */
    ConsultationResponse getMyLatest(UUID studentId);

    /** mine = false: các yêu cầu chưa ai nhận; mine = true: các yêu cầu mentor này đang / đã phụ trách. */
    PageResponse<ConsultationResponse> getForMentor(UUID mentorId, boolean mine, Pageable pageable);

    /** Mentor nhận tư vấn yêu cầu chưa ai nhận -> "contacted". */
    ConsultationResponse claim(UUID mentorId, UUID requestId);

    /** Mentor phụ trách cập nhật trạng thái: contacted / closed. */
    ConsultationResponse updateStatus(UUID mentorId, UUID requestId, ConsultationStatus status);

    /** Mentor tìm học viên (USER / STUDENT) theo tên / email / SĐT. */
    PageResponse<StudentOverviewResponse> searchStudents(String keyword, Pageable pageable);

    /** Hồ sơ tư vấn của 1 học viên. */
    StudentOverviewResponse getStudent(UUID studentId);
}
