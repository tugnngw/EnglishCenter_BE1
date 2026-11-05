package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.entity.PaymentTransaction;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {
    PaymentOrderResponse createOrder(UUID courseId);
    PaymentOrderResponse getOrder(String code);
    PaymentOrderResponse cancelOrder(String code);
    PageResponse<PaymentTransaction> myPayments(Pageable pageable);
    PageResponse<PaymentOrderResponse> myOrders(Pageable pageable);
    PageResponse<PaymentOrderResponse> adminOrders(PaymentOrderStatus status, Pageable pageable);
    PaymentOrderResponse adminOrder(UUID id);
    void processWebhook(byte[] body, String timestamp, String signature);

    // ===== Dev 5: thanh toán buổi kèm 1-1 (MENTOR_SESSION) =====
    /** Tạo đơn SePay cho booking pending vừa tạo; giá do BookingService lấy từ slot ở server. */
    PaymentOrderResponse createMentorSessionOrder(UUID studentId, UUID bookingId, BigDecimal amount, String title);
    /** Hủy đơn đang chờ của booking (khi booking pending bị hủy). Không có đơn chờ thì bỏ qua. */
    void cancelPendingBookingOrder(UUID bookingId);
    // Dev 5: đơn MENTOR_PLAN thanh toán 1 lần cả gói kèm 1-1; đã có đơn đang chờ thì trả lại đơn đó
    PaymentOrderResponse createMentorPlanOrder(UUID studentId, UUID planId, BigDecimal amount, String title);
    void cancelPendingPlanOrder(UUID planId);
}
