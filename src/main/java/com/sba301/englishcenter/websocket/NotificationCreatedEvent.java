package com.sba301.englishcenter.websocket;

import com.sba301.englishcenter.dto.response.NotificationResponse;

import java.util.UUID;

/**
 * Phát ra khi vừa lưu 1 thông báo; được đẩy qua WebSocket sau khi transaction commit (TASK-D5-02).
 */
public record NotificationCreatedEvent(UUID userId, NotificationResponse notification) {
}
