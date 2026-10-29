package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "student_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfile extends BaseEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "target_certificate_id")
    private UUID targetCertificateId;

    @Column(name = "target_score", precision = 6, scale = 2)
    private BigDecimal targetScore;

    /** Level CEFR gần nhất do AI chấm (A1 – C2). */
    @Column(name = "current_level", length = 20)
    private String currentLevel;

    /** Điểm gần nhất theo thang của chứng chỉ mục tiêu (VD IELTS 6.5, TOEIC 150). */
    @Column(name = "current_score", precision = 6, scale = 2)
    private BigDecimal currentScore;
}
