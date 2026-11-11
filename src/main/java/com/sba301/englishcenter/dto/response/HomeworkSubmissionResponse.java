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

    /**
     * Ai chấm bài này: {@code ai} (AI tự chấm) | {@code mentor} (mentor chấm tay).
     * <p>
     * null = chưa chấm. Bài trắc nghiệm tự chấm từ lượt thi nên không có giá trị này.
     */
    String gradedByType;

    /** Mentor được giao chấm bài này (null = học viên không mua mentor → AI chấm). */
    UUID assignedGraderId;

    /** Lịch sử chấm (mới nhất trước) — chỉ mentor/admin được thấy. */
    List<HomeworkGradeHistoryResponse> gradeHistory;

    /**
     * Rubric chấm điểm của bài tập (Admin cấu hình trong {@code content_data.rubric}).
     * <p>
     * Mentor dùng để chấm 1-1: mỗi tiêu chí có 1 ô điểm + 1 khung nhận xét.
     */
    List<HomeworkRubricCriterion> rubric;

    /** Điểm tối đa của bài tập (null = không giới hạn). */
    java.math.BigDecimal maxScore;

    /** Điểm đạt tối thiểu (%). */
    java.math.BigDecimal passingScore;

    /**
     * Gợi ý chấm từ AI (chỉ có sau khi mentor bấm nút "Xem gợi ý chấm từ AI").
     * <p>
     * AI KHÔNG chấm hộ — chỉ tham khảo để mentor chấm nhanh hơn.
     */
    HomeworkAiSuggestionResponse aiSuggestion;

    /** true = bài này là trắc nghiệm (hệ thống tự chấm, mentor chỉ xem thống kê). */
    Boolean autoGraded;

    /**
     * Nguồn của bài tập — dùng để CHIA MÀN hàng đợi chấm của mentor:
     * <ul>
     *   <li>{@code course} — bài tập MẶC ĐỊNH của khóa học (admin tạo);</li>
     *   <li>{@code mentor} — bài tập mentor tự tạo và gửi cho học viên.</li>
     * </ul>
     */
    String homeworkSource;

    /** Id khóa học chứa bài tập (null = bài tập riêng của mentor). */
    UUID courseId;

    /** Tên khóa học chứa bài tập (null = bài tập riêng của mentor). */
    String courseTitle;
}