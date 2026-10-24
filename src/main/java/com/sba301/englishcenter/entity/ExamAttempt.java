package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.enums.AttemptStatus;
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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một lượt thi của học viên.
 * <ul>
 *     <li>{@code in_progress}: đang làm, còn trước {@code expiresAt};</li>
 *     <li>{@code submitted}: học viên đã nộp, đang chờ mentor chấm Writing/Speaking;</li>
 *     <li>{@code graded}: đã nộp và chấm xong toàn bộ;</li>
 *     <li>{@code expired}: hết giờ, hệ thống tự nộp ({@code autoSubmitted = true}) và đã chấm phần tự động.</li>
 * </ul>
 * Bảng không có last_updated nên không kế thừa BaseEntity.
 */
@Entity
@Table(name = "exam_attempts")
@DynamicUpdate
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "attempt_id")
    private UUID attemptId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Builder.Default
    @Column(name = "attempt_number")
    private Integer attemptNumber = 1;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** Hạn chót làm bài = startTime + duration của đề. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** Thời điểm nộp (hoặc thời điểm hệ thống đóng bài khi hết giờ). */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** Tổng điểm đạt được (điểm thô, cộng điểm các câu). */
    @Builder.Default
    @Column(name = "score", precision = 6, scale = 2)
    private BigDecimal score = BigDecimal.ZERO;

    /** Tổng điểm tối đa của đề lúc bắt đầu làm bài. */
    @Builder.Default
    @Column(name = "max_score", precision = 8, scale = 2, nullable = false)
    private BigDecimal maxScore = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "passed")
    private Boolean passed = false;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Builder.Default
    @Column(name = "auto_submitted", nullable = false)
    private Boolean autoSubmitted = false;

    @Builder.Default
    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds = 0;

    @Builder.Default
    @Column(name = "correct_count")
    private Integer correctCount = 0;

    @Builder.Default
    @Column(name = "incorrect_count")
    private Integer incorrectCount = 0;

    @Builder.Default
    @Column(name = "unanswered_count")
    private Integer unansweredCount = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
