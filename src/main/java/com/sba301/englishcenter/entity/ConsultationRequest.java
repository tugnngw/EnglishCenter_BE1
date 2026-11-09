package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import com.sba301.englishcenter.common.enums.ConsultationStatus;
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
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

/**
 * Bảng consultation_requests (V21): học viên để lại SĐT nhờ mentor tư vấn lộ trình.
 * Mọi mentor đều thấy yêu cầu "pending"; mentor nhận trước (mentor_id) phụ trách liên hệ.
 * available_slots (V26): lịch rảnh hằng tuần để mentor xếp các buổi của gói kèm 1-1.
 */
@Entity
@Table(name = "consultation_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    // Mentor nhận tư vấn, null = chưa ai nhận
    @Column(name = "mentor_id")
    private UUID mentorId;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConsultationStatus status;

    // Khung giờ rảnh hằng tuần (JSONB, V26) - xem AvailableSlotData
    @Column(name = "available_slots", columnDefinition = "jsonb")
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String availableSlots;
}
