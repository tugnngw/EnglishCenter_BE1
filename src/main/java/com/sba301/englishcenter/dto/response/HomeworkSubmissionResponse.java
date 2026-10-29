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
    BigDecimal score;
    String feedback;
    LocalDateTime submittedDate;
    LocalDateTime gradedDate;

    /** true nghĩa là đã chấm điểm. */
    Boolean graded;

    /* =====================================================
     * Field bổ sung (V8) — nullable, tương thích ngược.
     * ===================================================== */

    /** TRUE = đạt; null = chưa chấm. */
    Boolean passed;

    /**
     * Trạng thái bài nộp cho UI:
     * {@code not_started} | {@code pending} (đã nộp, chờ chấm) | {@code graded} (đã có điểm).
     */
    String status;

    /** Hình thức nộp đã snapshot: text | file | multiple_choice. */
    String submissionType;

    /** Lượt thi thật của bài trắc nghiệm (null với tự luận / nộp file). */
    UUID examAttemptId;

    /** public_id file trên Cloudinary (URL được sinh riêng, có thời hạn, khi xem). */
    String filePublicId;

    /** image | raw | video. */
    String fileResourceType;

    String fileFormat;

    Long fileBytes;

    String fileOriginalName;

    /**
     * URL xem file ĐÃ CÓ THỜI HẠN — chỉ có khi người gọi được phép xem
     * (học viên xem bài của mình, hoặc mentor chấm bài của khóa mình dạy).
     */
    String fileUrl;

    /** Lịch sử chấm (mới nhất trước) — chỉ mentor/admin được thấy. */
    List<HomeworkGradeHistoryResponse> gradeHistory;
}