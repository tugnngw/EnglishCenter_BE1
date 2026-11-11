package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tiến độ học viên với một bài học (video) — bảng {@code lesson_progress} (V29).
 * <p>
 * Dùng cho 2 mục đích:
 * <ol>
 *   <li>Đánh dấu "đã hoàn thành" khi học viên xem hết video → tick hoàn thành ở danh sách bài học;</li>
 *   <li>Làm điều kiện mở khoá <b>Bài thi cuối kỳ</b>: chỉ mở khi đã xem xong TẤT CẢ video của khóa.</li>
 * </ol>
 * <p>
 * {@code watchedSeconds} là MỐC giây đã xem tối đa (không phải currentTime hiện tại) —
 * nhờ vậy client không thể tua video vượt quá mốc đã xem (chống tua).
 * <p>
 * <b>Khoá chính là composite {@code (content_id, student_id)}</b>: mỗi học viên có
 * một dòng tiến độ riêng cho mỗi bài học. (Nếu khoá chỉ theo {@code content_id}
 * thì học viên thứ 2 học cùng bài sẽ đụng khoá / ghi đè tiến độ của người trước.)
 */
@Entity
@Table(name = "lesson_progress")
@IdClass(LessonProgress.LessonProgressId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LessonProgress extends BaseEntity {

    /** Bài học (nội dung 'lesson') mà tiến độ này thuộc về. */
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private CourseContent content;

    /** Học viên sở hữu tiến độ này. */
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /** Mốc giây đã xem tối đa — client không được phát quá mốc này. */
    @Column(name = "watched_seconds", nullable = false)
    @Builder.Default
    private Integer watchedSeconds = 0;

    /** Tổng thời lượng video (giây) — dùng để biết đã xem xong chưa. */
    @Column(name = "duration_seconds", nullable = false)
    @Builder.Default
    private Integer durationSeconds = 0;

    /** Đã xem xong video → tick "hoàn thành". */
    @Column(name = "completed", nullable = false)
    @Builder.Default
    private Boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** Id bài học (phục vụ code Java, không phải cột riêng). */
    public UUID getContentId() {
        return content != null ? content.getContentId() : null;
    }

    /** Id học viên (phục vụ code Java, không phải cột riêng). */
    public UUID getStudentId() {
        return student != null ? student.getUserId() : null;
    }

    /** Khoá composite của bảng {@code lesson_progress}. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class LessonProgressId implements Serializable {
        private CourseContent content;
        private User student;
    }
}
