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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bài nộp của học viên cho một nội dung homework.
 * Bảng này KHÔNG kế thừa BaseEntity vì schema gốc không có created_at/last_updated,
 * mà dùng submitted_date / graded_date.
 */
@Entity
@Table(name = "homework_submissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "submission_id")
    private UUID submissionId;

    /** FK tới course_content có content_type = 'homework'. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "homework_content_id", nullable = false)
    private CourseContent homeworkContent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "answer_content", columnDefinition = "TEXT")
    private String answerContent;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "score", precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "submitted_date")
    private LocalDateTime submittedDate;

    @Column(name = "graded_date")
    private LocalDateTime gradedDate;

    /* =====================================================
     * Cột bổ sung (V8) — nullable, không ảnh hưởng dữ liệu cũ.
     * ===================================================== */

    /**
     * TRUE khi đạt điểm; {@code null} = chưa chấm (đang chờ mentor).
     * <p>
     * Tính khi chấm (trắc nghiệm: lúc nộp bài). Mentor sửa điểm → tính lại.
     */
    @Column(name = "passed")
    private Boolean passed;

    /**
     * Snapshot hình thức nộp tại lúc nộp: {@code text} | {@code file} | {@code multiple_choice}.
     * <p>
     * Lưu riêng (không đọc lại từ {@code course_content.content_data}) để lịch sử
     * bài nộp không đổi hành vi khi admin sửa bài tập về sau.
     */
    @Column(name = "submission_type", length = 20)
    private String submissionType;

    /** Lượt thi thật của bài trắc nghiệm. NULL với bài tự luận / nộp file. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_attempt_id")
    private ExamAttempt examAttempt;

    /** public_id của file trên Cloudinary — KHÔNG lưu URL (URL có thời hạn). */
    @Column(name = "file_public_id", length = 255)
    private String filePublicId;

    /** image | raw | video — quyết định cách dựng URL truy cập. */
    @Column(name = "file_resource_type", length = 20)
    private String fileResourceType;

    @Column(name = "file_format", length = 20)
    private String fileFormat;

    @Column(name = "file_bytes")
    private Long fileBytes;

    @Column(name = "file_original_name", length = 255)
    private String fileOriginalName;
}