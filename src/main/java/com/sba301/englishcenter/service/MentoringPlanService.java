package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.CreateMentoringPlanRequest;
import com.sba301.englishcenter.dto.response.MentoringPlanResponse;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Gói kèm 1-1 theo lộ trình (Dev 5, V21) - thay cho slot công khai "ai vào thì vào".
 * Luồng: học viên test level -> để lại SĐT -> mentor tư vấn -> chốt lộ trình -> mentor tạo gói N buổi
 * -> học viên thanh toán 1 lần cả gói (SePay) -> các buổi được xác nhận.
 */
public interface MentoringPlanService {

    /** Mentor tạo gói cho 1 học viên: tạo các buổi (giữ riêng cho học viên) + booking "pending". */
    MentoringPlanResponse createPlan(UUID mentorId, CreateMentoringPlanRequest request);

    /** Các gói mentor đã tạo, mới nhất trước. */
    PageResponse<MentoringPlanResponse> getMentorPlans(UUID mentorId, Pageable pageable);

    /** Các gói của học viên, mới nhất trước. */
    List<MentoringPlanResponse> getMyPlans(UUID studentId);

    /** Chi tiết gói: chỉ mentor / học viên của gói hoặc admin. */
    MentoringPlanResponse getPlan(UUID requesterId, boolean isAdmin, UUID planId);

    /** Học viên thanh toán cả gói: tạo (hoặc lấy lại) đơn SePay MENTOR_PLAN. */
    PaymentOrderResponse checkout(UUID studentId, UUID planId);

    /** Học viên từ chối gói chưa thanh toán. */
    MentoringPlanResponse rejectByStudent(UUID studentId, UUID planId, String reason);

    /** Mentor hủy gói chưa thanh toán. */
    MentoringPlanResponse cancelByMentor(UUID mentorId, UUID planId, String reason);
}
