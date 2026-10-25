package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.dto.response.NotificationResponse;
import com.sba301.englishcenter.entity.Notification;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.NotificationRepository;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import com.sba301.englishcenter.websocket.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    // Email người nhận thuộc domain Dev 1 -> chỉ gọi qua service interface
    private final UserService userService;
    // Đẩy realtime qua WebSocket sau khi commit (NotificationPushListener)
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void notify(UUID userId, NotificationType type, String title, String message) {
        save(Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message));
    }

    @Override
    @Transactional
    public void notifyMentorSession(UUID userId, String title, String message,
                                    UUID relatedScheduleId, UUID relatedBookingId) {
        save(Notification.builder()
                .userId(userId)
                .type(NotificationType.mentor_session)
                .title(title)
                .message(message)
                .relatedMentorScheduleId(relatedScheduleId)
                .relatedBookingId(relatedBookingId));
    }

    // Lưu thông báo (email người nhận lấy qua UserService của Dev 1) rồi phát event để đẩy WebSocket sau commit
    private void save(Notification.NotificationBuilder builder) {
        Notification notification = builder.build();
        notification.setRecipientEmail(userService.getUserById(notification.getUserId()).getEmail());
        notification.setIsRead(false);
        notification.setCreatedAt(UtcTimeUtil.nowUtc());
        notification = notificationRepository.save(notification);
        log.info("Created {} notification for user {}", notification.getType(), notification.getUserId());
        eventPublisher.publishEvent(new NotificationCreatedEvent(notification.getUserId(), toResponse(notification)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getMyNotifications(UUID userId, Pageable pageable) {
        // Luôn sắp mới nhất trước, bỏ qua sort client gửi lên
        Page<Notification> page = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        return PageResponse.<NotificationResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(this::toResponse).toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID userId, UUID notificationId) {
        // Chỉ đọc được thông báo của chính mình
        Notification notification = notificationRepository.findByNotificationIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(UtcTimeUtil.nowUtc());
            notification = notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsRead(userId, UtcTimeUtil.nowUtc());
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .type(notification.getType() != null ? notification.getType().name() : null)
                .title(notification.getTitle())
                .message(notification.getMessage())
                .isRead(notification.getIsRead())
                .readAt(UtcTimeUtil.toInstant(notification.getReadAt()))
                .createdAt(UtcTimeUtil.toInstant(notification.getCreatedAt()))
                .build();
    }
}
