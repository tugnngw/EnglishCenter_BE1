package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông báo trong ứng dụng (TASK D5-16). Khớp type AppNotification ở FE.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private UUID notificationId;
    private String type;
    private String title;
    private String message;
    private Boolean isRead;
    private Instant readAt;
    private Instant createdAt;
}
