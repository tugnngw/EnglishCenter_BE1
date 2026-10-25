package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Bảng learning_paths (V1): lộ trình học theo chứng chỉ (TASK-D5-03).
 * - items: danh sách chặng (JSONB) — xem LearningPathItemData.
 * - student_progress: tiến độ từng học viên (JSONB) — xem StudentProgressData.
 * JSONB map dạng String + cast(? as jsonb) giống CourseContent.contentData của Dev 2.
 * certificate_id / created_by_user_id chỉ lưu UUID (không map entity của module khác).
 */
@Entity
@Table(name = "learning_paths")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPath extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "learning_path_id")
    private UUID learningPathId;

    @Column(name = "certificate_id", nullable = false)
    private UUID certificateId;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "items", columnDefinition = "jsonb")
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String items;

    @Column(name = "student_progress", columnDefinition = "jsonb")
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String studentProgress;
}
