package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import com.sba301.englishcenter.common.enums.ScheduleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bảng mentor_schedules: slot thời gian rảnh do mentor mở (AvailabilitySlot - TASK D5-01).
 * Học viên đặt slot được lưu ở bảng bookings ({@link Booking}), cột student_id của bảng này không dùng nữa.
 * mentor_id lưu UUID (không map quan hệ sang User để không phụ thuộc entity của Dev 1).
 * start_time / end_time lưu giờ UTC (TASK D5-19).
 * Trạng thái: available = đang mở, booked = đã có người đặt, cancelled = mentor đóng slot, completed = đã dạy xong.
 */
@Entity
@Table(name = "mentor_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorSchedule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    private ScheduleStatus status;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "meeting_url", length = 500)
    private String meetingUrl;
}
