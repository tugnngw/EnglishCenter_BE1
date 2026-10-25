package com.sba301.englishcenter.service.impl;

import static org.mockito.Mockito.lenient;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import com.sba301.englishcenter.websocket.NotificationCreatedEvent;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.dto.response.NotificationResponse;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.entity.Notification;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.NotificationRepository;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserService userService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private final UUID userId = UUID.randomUUID();
    private final UUID notificationId = UUID.randomUUID();

    private Notification unread() {
        return Notification.builder()
                .notificationId(notificationId)
                .userId(userId)
                .type(NotificationType.mentor_session)
                .title("Đặt lịch thành công")
                .recipientEmail("student@test.com")
                .isRead(false)
                .createdAt(UtcTimeUtil.nowUtc())
                .build();
    }

    @BeforeEach
    void setUp() {
        lenient().when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            if (n.getNotificationId() == null) {
                n.setNotificationId(UUID.randomUUID());
            }
            return n;
        });
    }

    @Test
    @DisplayName("Hub thông báo dùng chung: lưu đúng loại, chưa đọc và phát event để đẩy WebSocket sau commit")
    void notify_savesAndPublishesEvent() {
        when(userService.getUserById(userId))
                .thenReturn(UserResponse.builder().userId(userId).email("student@test.com").build());

        notificationService.notify(userId, NotificationType.payment, "Thanh toán thành công", "Đơn EC1000000001");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.payment);
        assertThat(captor.getValue().getIsRead()).isFalse();
        ArgumentCaptor<NotificationCreatedEvent> event = ArgumentCaptor.forClass(NotificationCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().userId()).isEqualTo(userId);
        assertThat(event.getValue().notification().getTitle()).isEqualTo("Thanh toán thành công");
        assertThat(event.getValue().notification().getIsRead()).isFalse();
    }

    @Test
    @DisplayName("Tạo thông báo: loại mentor_session, chưa đọc, email lấy từ hồ sơ người dùng")
    void notifyMentorSession_success() {
        UUID bookingId = UUID.randomUUID();
        when(userService.getUserById(userId))
                .thenReturn(UserResponse.builder().userId(userId).email("student@test.com").build());

        notificationService.notifyMentorSession(userId, "Tiêu đề", "Nội dung", null, bookingId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.mentor_session);
        assertThat(saved.getRecipientEmail()).isEqualTo("student@test.com");
        assertThat(saved.getIsRead()).isFalse();
        assertThat(saved.getRelatedBookingId()).isEqualTo(bookingId);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Đánh dấu đã đọc: set isRead + readAt")
    void markAsRead_success() {
        when(notificationRepository.findByNotificationIdAndUserId(notificationId, userId))
                .thenReturn(Optional.of(unread()));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(userId, notificationId);

        assertThat(response.getIsRead()).isTrue();
        assertThat(response.getReadAt()).isNotNull();
        assertThat(response.getType()).isEqualTo("mentor_session");
    }

    @Test
    @DisplayName("Đọc thông báo của người khác -> NOTIFICATION_NOT_FOUND")
    void markAsRead_otherUser() {
        UUID otherUserId = UUID.randomUUID();
        when(notificationRepository.findByNotificationIdAndUserId(notificationId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(otherUserId, notificationId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đọc tất cả: trả về số thông báo vừa đánh dấu")
    void markAllAsRead() {
        when(notificationRepository.markAllAsRead(eq(userId), any())).thenReturn(3);

        assertThat(notificationService.markAllAsRead(userId)).isEqualTo(3);
    }
}
