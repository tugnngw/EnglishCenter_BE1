package com.sba301.englishcenter.entity;

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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Liên kết "gán bộ từ vựng chung" vào một khóa học.
 * <p>
 * Đây là bảng trung gian cho quan hệ Many-to-Many giữa
 * {@link Course} và {@link VocabularySet}. Từ vựng được tạo độc lập trong
 * kho chung; Admin/Mentor tick chọn trong "Quản lý nội dung khóa học" để
 * gán bộ này vào khóa học tương ứng.
 */
@Entity
@Table(name = "course_vocabulary_sets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseVocabularySet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id", nullable = false)
    private VocabularySet vocabularySet;

    /** Thứ tự hiển thị trong khóa học. */
    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    /** Admin/Mentor đã gán. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_user_id")
    private User assignedByUser;

    @Builder.Default
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();
}
