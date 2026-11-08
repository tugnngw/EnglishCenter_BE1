package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Bảng thống kê kết quả làm bài của học viên cho bài tập TRẮC NGIỆM (Nhóm A).
 * <p>
 * Bài trắc nghiệm được hệ thống chấm tự động → mentor KHÔNG cần thao tác chấm,
 * chỉ xem bảng này để nắm điểm yếu chung của học viên.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HomeworkExamStatsResponse {

    UUID homeworkContentId;
    String homeworkTitle;

    /** Số học viên đã nộp bài. */
    Integer totalSubmissions;

    /** Điểm trung bình (thang của đề). */
    BigDecimal averageScore;

    /** Điểm tối đa của đề. */
    BigDecimal maxScore;

    /** Tỉ lệ đạt trung bình (%). */
    BigDecimal averagePercentage;

    /** Số học viên đạt. */
    Integer passedCount;

    /** Số học viên không đạt. */
    Integer failedCount;

    /** Kết quả từng học viên. */
    List<HomeworkExamStudentResult> studentResults;

    /** Điểm yếu chung: câu nào học viên sai nhiều nhất. */
    List<HomeworkExamQuestionStat> questionStats;

    /** Kết quả của 1 học viên. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HomeworkExamStudentResult {
        UUID studentId;
        String studentName;
        String studentEmail;
        BigDecimal score;
        BigDecimal maxScore;
        BigDecimal percentage;
        Boolean passed;
        LocalDateTime submittedDate;
    }

    /** Thống kê 1 câu hỏi (dùng để tìm điểm yếu). */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class HomeworkExamQuestionStat {
        UUID questionId;
        Integer displayNumber;
        String questionText;
        String questionType;
        BigDecimal points;
        Integer correctCount;
        Integer incorrectCount;
        Integer unansweredCount;
        /** Tỉ lệ trả lời đúng (%). */
        BigDecimal correctRate;
    }
}
