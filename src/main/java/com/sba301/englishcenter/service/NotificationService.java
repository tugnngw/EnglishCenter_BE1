package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.dto.response.NotificationResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    /**
     * Hub thông báo dùng chung cho các module khác (TASK-D5-02): Dev 2 (bài tập mới), Dev 3 (kết quả thi),
     * Dev 4 (thanh toán thành công)... chỉ cần gọi hàm này.
     * Thông báo được lưu trong transaction hiện tại và đẩy realtime qua WebSocket
     * /topic/notifications/{userId} sau khi transaction commit.
     */
    void notify(UUID userId, NotificationType type, String title, String message);

    /**
     * Tạo thông báo loại mentor_session cho 1 người dùng (dùng nội bộ khi đặt / hủy / đổi lịch - TASK D5-16).
     */
    void notifyMentorSession(UUID userId, String title, String message,
                             UUID relatedScheduleId, UUID relatedBookingId);

    /**
     * Thông báo của người dùng đang đăng nhập, mới nhất trước.
     */
    PageResponse<NotificationResponse> getMyNotifications(UUID userId, Pageable pageable);

    /**
     * Số thông báo chưa đọc.
     */
    long getUnreadCount(UUID userId);

    /**
     * Đánh dấu 1 thông báo là đã đọc.
     */
    NotificationResponse markAsRead(UUID userId, UUID notificationId);

    /**
     * Đánh dấu tất cả là đã đọc, trả về số thông báo vừa được đánh dấu.
     */
    int markAllAsRead(UUID userId);
}
