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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lịch sử chấm / sửa điểm của một bài nộp bài tập.
 * <p>
 * Mỗi lần mentor nhập điểm (lần đầu hoặc sửa lại) tạo 1 dòng ghi lại:
 * ai chấm, điểm cũ, điểm mới, kết quả đạt/không đạt và thời điểm.
 * Bảng mới (V8) — không ảnh hưởng dữ liệu cũ.
 */
@Entity
@Table(name = "homework_grade_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HomeworkGradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "history_id")
    private UUID historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submission_id", nullable = false)
    private HomeworkSubmission submission;

    /** Mentor (hoặc admin) thực hiện chấm. NULL nếu user đã bị xoá. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by_user_id")
    private User gradedBy;

    /** Điểm trước khi sửa. NULL ở lần chấm đầu tiên. */
    @Column(name = "old_score", precision = 6, scale = 2)
    private BigDecimal oldScore;

    @Column(name = "new_score", precision = 6, scale = 2)
    private BigDecimal newScore;

    /** Kết quả đạt/không đạt sau khi tính lại. */
    @Column(name = "passed")
    private Boolean passed;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "graded_at")
    private LocalDateTime gradedAt;

    /**
     * Ai thực hiện lần chấm/sửa điểm này: {@code ai} | {@code mentor}.
     * <p>
     * NULL = chưa chấm. Thêm ở V23.
     */
    @Column(name = "graded_by_type", length = 20)
    private String gradedByType;

    /**
     * Snapshot rubric điểm tại lần chấm này (JSONB).
     * <p>
     * Thêm ở V24.
     */
    @Column(name = "rubric_scores", columnDefinition = "jsonb")
    @ColumnTransformer(write = "cast(? as jsonb)")
    private String rubricScores;
}
