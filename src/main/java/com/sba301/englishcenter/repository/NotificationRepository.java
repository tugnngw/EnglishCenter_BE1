package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(UUID userId);

    Optional<Notification> findByNotificationIdAndUserId(UUID notificationId, UUID userId);

    // Đánh dấu tất cả thông báo chưa đọc của 1 người dùng là đã đọc, trả về số dòng cập nhật
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt "
            + "WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);
}
