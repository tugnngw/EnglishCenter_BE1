package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sba301.englishcenter.common.enums.AttemptStatus;
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
 * Một dòng trong lịch sử thi / hàng đợi chấm bài.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttemptSummaryResponse {

    UUID attemptId;
    UUID examId;
    String examTitle;
    String examCode;
    Integer attemptNumber;
    AttemptStatus status;
    LocalDateTime startTime;
    LocalDateTime expiresAt;
    LocalDateTime endTime;
    Integer timeSpentSeconds;

    /** null nếu đề không cho xem điểm (show_score = false). */
    BigDecimal score;
    BigDecimal maxScore;
    BigDecimal percentage;

    /** null khi còn câu chờ chấm tay hoặc đề không cho xem điểm. */
    Boolean passed;
    Boolean pendingManualGrading;
    Boolean autoSubmitted;

    UUID studentId;
    String studentName;
    String studentEmail;
}
