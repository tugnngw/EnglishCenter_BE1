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
 * Câu trả lời của học viên cho một câu hỏi trong một lượt thi (bảng {@code exam_attempt_details}).
 * <ul>
 *     <li>multiple_choice / true_false: {@code selectedOption};</li>
 *     <li>fill_blank: {@code answerText};</li>
 *     <li>matching: {@code answerText} là JSON object {@code {"vế trái": "vế phải"}};</li>
 *     <li>essay (Writing): {@code answerText} là bài viết; Speaking: {@code answerText} là URL file ghi âm.</li>
 * </ul>
 */
@Entity
@Table(name = "exam_attempt_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "detail_id")
    private UUID detailId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_bank_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOption selectedOption;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Builder.Default
    @Column(name = "is_correct")
    private Boolean isCorrect = false;

    /** Điểm đạt được của câu: tự động (objective) hoặc bằng {@code manualScore} (essay). */
    @Builder.Default
    @Column(name = "earned_score", precision = 6, scale = 2, nullable = false)
    private BigDecimal earnedScore = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "time_spent_seconds")
    private Integer timeSpentSeconds = 0;

    @Column(name = "answered_date")
    private LocalDateTime answeredDate;

    @Column(name = "manual_score", precision = 6, scale = 2)
    private BigDecimal manualScore;

    @Column(name = "mentor_feedback", columnDefinition = "TEXT")
    private String mentorFeedback;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(name = "graded_date")
    private LocalDateTime gradedDate;
}
