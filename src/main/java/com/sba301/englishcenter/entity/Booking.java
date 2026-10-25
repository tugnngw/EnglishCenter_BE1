package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import com.sba301.englishcenter.common.enums.BookingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bảng bookings: 1 lượt học viên đặt 1 slot của mentor (TASK D5-01, D5-11).
 * Hủy / đổi lịch không xóa dòng mà chuyển trạng thái để giữ lịch sử.
 * mentor_id / student_id lưu UUID (không map quan hệ sang User để không phụ thuộc entity của Dev 1).
 * cancelled_at lưu giờ UTC (TASK D5-19).
 */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "booking_id")
    private UUID bookingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private MentorSchedule schedule;

    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    // Booking cũ (đã hủy) mà booking này được đổi lịch từ đó
    @Column(name = "rescheduled_from_id")
    private UUID rescheduledFromId;

    // Thời điểm (UTC) đã gửi nhắc "sắp đến giờ học" — null = chưa nhắc (V9, TASK-D5-02 / D5-18)
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;
}
