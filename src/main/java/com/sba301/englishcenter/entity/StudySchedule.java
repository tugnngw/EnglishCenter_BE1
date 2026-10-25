package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.enums.RepeatRule;
import com.sba301.englishcenter.common.enums.StudyStatus;
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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bảng study_schedules: lịch nhắc học tập cá nhân của học viên (TASK-D5-01, USER_FLOWS 1.8).
 * Bảng không có last_updated nên không kế thừa BaseEntity (giống Notification).
 * scheduled_time / reminder_sent_at / created_at lưu giờ UTC (TASK D5-19).
 * lesson_content_id chỉ lưu UUID (không map quan hệ sang CourseContent của Dev 2).
 */
@Entity
@Table(name = "study_schedules")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudySchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "lesson_content_id")
    private UUID lessonContentId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "repeat_rule")
    private RepeatRule repeatRule;

    @Column(name = "reminder_minutes_before")
    private Integer reminderMinutesBefore;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    private StudyStatus status;

    // Đã gửi nhắc cho lần học hiện tại chưa (V10); lịch lặp lại được reset khi chuyển sang lần kế tiếp
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
