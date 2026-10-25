package com.sba301.englishcenter.service;

import java.util.UUID;

/**
 * Cầu nối Dev 4 (PaymentService) -> Dev 5 (booking) cho đơn MENTOR_SESSION.
 * Tách riêng khỏi BookingService để PaymentServiceImpl không phụ thuộc vòng vào BookingServiceImpl.
 * Tất cả được gọi bên trong transaction của PaymentServiceImpl.
 */
public interface BookingPaymentService {

    /**
     * Webhook báo đã thanh toán: booking pending -> confirmed, gửi thông báo cho học viên và mentor.
     *
     * @return scheduleId của buổi học; null nếu booking không còn chờ thanh toán (đơn sẽ chuyển REVIEW_REQUIRED)
     */
    UUID confirmPaidBooking(UUID bookingId);

    /**
     * Đơn hết hạn / bị hủy: booking pending -> cancelled, mở lại slot. Booking không còn pending thì bỏ qua.
     */
    void releaseUnpaidBooking(UUID bookingId, String reason);
}
