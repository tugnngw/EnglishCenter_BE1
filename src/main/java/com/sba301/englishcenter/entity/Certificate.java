package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import com.sba301.englishcenter.common.enums.StandardCertificateScale;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "certificates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Certificate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "certificate_id")
    private UUID certificateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "certificate_name", nullable = false)
    private String certificateName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "min_score", precision = 6, scale = 2)
    private BigDecimal minScore = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "max_score", precision = 6, scale = 2)
    private BigDecimal maxScore = BigDecimal.TEN;

    @Builder.Default
    @Column(name = "score_step", precision = 6, scale = 2)
    private BigDecimal scoreStep = new BigDecimal("0.50");

    @Builder.Default
    @Column(name = "score_unit", length = 50)
    private String scoreUnit = "Điểm";

    @Builder.Default
    @Column(name = "scale_type", length = 50)
    private String scaleType = "AVERAGE";

    @Column(name = "rounding_rule", length = 50)
    private String roundingRule;

    /** Thang chuẩn mà chứng chỉ đang dựa trên (IELTS / TOEIC / TOEFL / VSTEP); null = tùy chỉnh. */
    @Enumerated(EnumType.STRING)
    @Column(name = "standard_scale", length = 50)
    private StandardCertificateScale standardScale;

    @OneToMany(mappedBy = "certificate", fetch = FetchType.LAZY)
    @Builder.Default
    private List<CertificatePart> parts = new ArrayList<>();
}