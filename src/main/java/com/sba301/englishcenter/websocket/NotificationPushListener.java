package com.sba301.englishcenter.websocket;

import com.sba301.englishcenter.config.WebSocketConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Đẩy thông báo realtime tới /topic/notifications/{userId} SAU KHI transaction lưu thông báo đã commit,
 * để client không nhận thông báo của nghiệp vụ bị rollback (TASK-D5-02).
 * Lỗi đẩy WebSocket không ảnh hưởng nghiệp vụ: client vẫn đọc được qua GET /api/v1/notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        try {
            messagingTemplate.convertAndSend(WebSocketConfig.NOTIFICATION_TOPIC_PREFIX + event.userId(), event.notification());
        } catch (RuntimeException ex) {
            log.warn("Could not push notification to user {}: {}", event.userId(), ex.getMessage());
        }
    }
}
