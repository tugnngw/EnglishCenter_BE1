package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tiến độ học tập của 1 học viên cho mentor phụ trách xem (Dev 5):
 * % khóa học, điểm bài tập, tiến độ lộ trình, số buổi kèm 1-1 với mentor này.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProgressResponse {

    private List<CourseProgress> courses;
    private List<HomeworkScore> homeworks;
    private List<LearningPathProgressResponse> learningPaths;
    private SessionSummary sessions;

    /** % khóa học = số bài học (có video) đã xem xong / tổng số bài học có video */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseProgress {
        private UUID courseId;
        private String courseTitle;
        private String thumbnailUrl;
        private int completedLessons;
        private int totalLessons;
        private int progressPercent;
    }

    /** 1 bài tập (gộp các lần nộp): điểm của lần chấm gần nhất */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HomeworkScore {
        private UUID homeworkId;
        private String title;
        // null = bài tập mentor giao (không thuộc khóa học)
        private String courseTitle;
        private BigDecimal score;
        private BigDecimal maxScore;
        private BigDecimal passingScore;
        private Boolean passed;
        // Lần nộp mới nhất chưa được chấm
        private boolean pendingGrading;
        private int attemptCount;
        private LocalDateTime lastSubmittedDate;
        private LocalDateTime gradedDate;
    }

    /** Các buổi kèm 1-1 giữa học viên và mentor đang xem */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionSummary {
        private int total;
        private int completed;
        private int noShow;
        private int cancelled;
        private int upcoming;
    }
}
