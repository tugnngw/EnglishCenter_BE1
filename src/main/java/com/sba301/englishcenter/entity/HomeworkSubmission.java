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
}