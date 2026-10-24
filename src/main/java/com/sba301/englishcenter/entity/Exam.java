package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import com.sba301.englishcenter.common.converters.ExamTypeConverter;
import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.ExamType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Đề thi. Có hai phạm vi ({@link ExamScope}):
 * <ul>
 *     <li>{@code trial}  - thi thử theo chứng chỉ (bắt buộc {@code certificate});</li>
 *     <li>{@code course} - bài thi của một khóa học (bắt buộc {@code course}, cần enrollment).</li>
 * </ul>
 * Cột JSONB {@code sections} của V1 là thiết kế cũ, không còn dùng; cấu trúc section nay nằm ở bảng
 * {@code exam_sections} (V5). {@code total_questions}, {@code total_parts} do service tự cập nhật.
 */
@Entity
@Table(name = "exams")
@DynamicUpdate
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Exam extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "exam_id")
    private UUID examId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "exam_scope", nullable = false)
    private ExamScope examScope;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "certificate_id")
    private Certificate certificate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @Column(name = "exam_code", length = 50)
    private String examCode;

    /**
     * Nhãn DB 'final' trùng từ khóa Java nên KHÔNG dùng được {@code NAMED_ENUM} (Hibernate cần tên hằng số
     * trùng nhãn DB). Thay vào đó: {@link ExamTypeConverter} đổi sang chuỗi, {@code cast(? as exam_type_enum)}
     * khi ghi. Khi lọc theo cột này phải so sánh dạng chuỗi - xem {@code ExamSpecification}.
     */
    @Convert(converter = ExamTypeConverter.class)
    @ColumnTransformer(write = "cast(? as exam_type_enum)")
    @Column(name = "exam_type")
    private ExamType examType;

    @Column(name = "exam_title", nullable = false)
    private String examTitle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Builder.Default
    @Column(name = "total_questions")
    private Integer totalQuestions = 0;

    /** Phần trăm tối thiểu (0-100) trên tổng điểm để đạt. */
    @Builder.Default
    @Column(name = "passing_score", precision = 6, scale = 2)
    private BigDecimal passingScore = new BigDecimal("60.00");

    /** Số lượt thi tối đa mỗi học viên; null hoặc <= 0 nghĩa là không giới hạn. */
    @Builder.Default
    @Column(name = "max_attempts")
    private Integer maxAttempts = 1;

    @Builder.Default
    @Column(name = "show_answers")
    private Boolean showAnswers = true;

    @Builder.Default
    @Column(name = "show_score")
    private Boolean showScore = true;

    @Builder.Default
    @Column(name = "is_free")
    private Boolean isFree = true;

    @Builder.Default
    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "difficulty_level")
    private DifficultyLevel difficultyLevel = DifficultyLevel.MEDIUM;

    @Column(name = "recommended_level", length = 100)
    private String recommendedLevel;

    @Builder.Default
    @Column(name = "total_parts")
    private Integer totalParts = 0;

    @Builder.Default
    @Column(name = "total_attempts")
    private Integer totalAttempts = 0;

    @Builder.Default
    @Column(name = "average_score", precision = 6, scale = 2)
    private BigDecimal averageScore = BigDecimal.ZERO;

    @Column(name = "available_from")
    private LocalDateTime availableFrom;

    @Column(name = "available_until")
    private LocalDateTime availableUntil;

    @Column(name = "published_date")
    private LocalDateTime publishedDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status")
    private ExamStatus status = ExamStatus.DRAFT;
}
