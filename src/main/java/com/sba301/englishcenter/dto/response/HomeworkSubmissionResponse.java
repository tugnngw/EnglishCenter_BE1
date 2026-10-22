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
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HomeworkSubmissionResponse {

    UUID submissionId;
    UUID homeworkContentId;
    String homeworkTitle;

    UUID studentId;
    String studentName;
    String studentEmail;

    String answerContent;
    String fileUrl;
    BigDecimal score;
    String feedback;
    LocalDateTime submittedDate;
    LocalDateTime gradedDate;

    /** true nghĩa là đã chấm điểm. */
    Boolean graded;
}