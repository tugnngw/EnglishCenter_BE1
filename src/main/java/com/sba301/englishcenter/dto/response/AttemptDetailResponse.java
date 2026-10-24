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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Trạng thái một lượt thi. Khi status = in_progress có kèm đề (không có đáp án đúng) và các câu đã lưu
 * để frontend dựng phòng thi / tiếp tục làm bài sau khi tải lại trang.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttemptDetailResponse {

    UUID attemptId;
    UUID examId;
    String examTitle;
    Integer attemptNumber;
    AttemptStatus status;
    LocalDateTime startTime;
    LocalDateTime expiresAt;

    /** Số giây còn lại tính theo giờ server - frontend dùng để đồng bộ đồng hồ đếm ngược. */
    Long remainingSeconds;
    LocalDateTime endTime;
    Integer durationMinutes;
    Boolean autoSubmitted;

    ExamDetailResponse exam;
    List<SavedAnswerResponse> answers;
}
