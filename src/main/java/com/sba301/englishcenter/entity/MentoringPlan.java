package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import com.sba301.englishcenter.common.enums.MentoringPlanStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bảng mentoring_plans (V21): gói kèm 1-1 mentor tạo cho 1 học viên sau khi tư vấn.
 * Gồm N buổi (mentor_schedules.plan_id), học viên thanh toán 1 lần cả gói (đơn SePay MENTOR_PLAN).
 * mentor_id / student_id lưu UUID (không map quan hệ sang User, giống Booking).
 */
@Entity
@Table(name = "mentoring_plans")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentoringPlan extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    // Lộ trình gói bám theo (có thể trống nếu mentor tư vấn riêng)
    @Column(name = "learning_path_id")
    private UUID learningPathId;

    // Yêu cầu tư vấn dẫn tới gói này
    @Column(name = "consultation_id")
    private UUID consultationId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "total_sessions", nullable = false)
    private Integer totalSessions;

    // Tổng học phí = tổng giá các buổi (hourly_rate x số giờ mỗi buổi)
    @Column(name = "total_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MentoringPlanStatus status;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    // Giờ UTC lúc nhận tiền
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
