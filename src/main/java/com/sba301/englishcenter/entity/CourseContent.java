package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import com.sba301.englishcenter.common.enums.ContentType;
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
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Nội dung khóa học - gộp chung lesson + vocabulary + homework trong một bảng.
 * <p>
 * Các trường riêng theo từng loại (isPreview, videoUrl, durationMinutes, word, phonetic...)
 * được lưu trong {@code contentData} dạng JSONB, không thêm cột riêng.
 */
@Entity
@Table(name = "course_content")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseContent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "content_id")
    private UUID contentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /**
     * Cột `content_type` là PostgreSQL native enum (`content_type_enum`), KHÔNG phải
     * VARCHAR. Dùng `@Convert` sẽ khiến Hibernate bind `character varying` và
     * PostgreSQL báo lỗi `operator does not exist: content_type_enum = character varying`
     * cho mọi câu query có điều kiện theo loại nội dung.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    /** Nội dung cha (dùng để gom nhóm bài học theo chương). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_content_id")
    private CourseContent parentContent;

    @Column(name = "title")
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "content_order")
    private Integer contentOrder = 0;

    /**
     * Cột JSONB {@code content_data} của PostgreSQL.
     * Chứa các thuộc tính riêng: isPreview, videoUrl, durationMinutes, word, phonetic, audioUrl...
     * <p>
     * Cần {@code @ColumnTransformer(write = "cast(? as jsonb)")} vì chỉ dùng
     * {@code columnDefinition = "jsonb"} thì Hibernate vẫn bind giá trị kiểu
     * {@code VARCHAR}, khiến PostgreSQL từ chối với
     * {@code column "content_data" is of type jsonb but expression is of type character varying}.
     */
    @Column(name = "content_data", columnDefinition = "jsonb")
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String contentData;
}