package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.ExamType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thông tin đề thi (danh sách + phần đầu của chi tiết).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamSummaryResponse {

    UUID examId;
    String examCode;
    String examTitle;
    String description;
    ExamScope examScope;
    ExamType examType;
    ExamStatus status;

    UUID certificateId;
    String certificateName;
    UUID courseId;
    String courseTitle;

    Integer durationMinutes;
    Integer totalQuestions;
    Integer totalParts;
    BigDecimal passingScore;
    Integer maxAttempts;
    Boolean showAnswers;
    Boolean showScore;
    Boolean isFree;
    BigDecimal price;
    DifficultyLevel difficultyLevel;
    String recommendedLevel;

    LocalDateTime availableFrom;
    LocalDateTime availableUntil;
    LocalDateTime publishedDate;

    /** true nếu là đề của khóa học (phải có enrollment mới được làm). */
    Boolean requiresEnrollment;

    /** Thống kê - chỉ trả cho admin. */
    Integer totalAttempts;
    BigDecimal averageScore;
    LocalDateTime createdAt;
    LocalDateTime lastUpdated;
}
