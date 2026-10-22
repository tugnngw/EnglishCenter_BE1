package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.ColumnTransformer;

import java.util.UUID;

@Entity
@Table(name = "certificate_parts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertificatePart extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "part_id")
    private UUID partId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id", nullable = false)
    private Certificate certificate;

    @Column(name = "part_name", nullable = false)
    private String partName;

    @Column(name = "part_description", columnDefinition = "TEXT")
    private String partDescription;

    @Column(name = "part_order", nullable = false)
    private Integer partOrder;

    /**
     * Cột JSONB {@code properties} của PostgreSQL.
     * <p>
     * Phải có {@code @ColumnTransformer(write = "cast(? as jsonb)")}. Chỉ dùng
     * {@code columnDefinition = "jsonb"} là KHÔNG đủ: nó chỉ ảnh hưởng DDL, còn
     * Hibernate vẫn bind giá trị kiểu {@code VARCHAR} → PostgreSQL báo lỗi
     * {@code column "properties" is of type jsonb but expression is of type character varying}
     * và endpoint tạo chứng chỉ trả về 500.
     */
    @Column(name = "properties", columnDefinition = "jsonb")
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String properties;
}