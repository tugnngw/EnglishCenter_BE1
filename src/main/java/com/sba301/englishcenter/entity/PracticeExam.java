package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import com.sba301.englishcenter.common.enums.PracticeExamType;
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

import java.util.UUID;

/**
 * Bảng practice_exams (V1): kho đề luyện tập Writing / Speaking (TASK-D5-04).
 * Bài làm + kết quả AI chấm lưu trong cột answers (JSONB, mảng PracticeAnswerData) theo task.md.
 * course_id / created_by_user_id chỉ lưu UUID (không map entity của module khác).
 */
@Entity
@Table(name = "practice_exams")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeExam extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "practice_exam_id")
    private UUID practiceExamId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "course_id")
    private UUID courseId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private Certificate certificate;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "title", nullable = false)
    private String title;

    // Đề bài
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 50)
    private PracticeExamType examType;

    @Column(name = "answers", columnDefinition = "jsonb")
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String answers;
}
