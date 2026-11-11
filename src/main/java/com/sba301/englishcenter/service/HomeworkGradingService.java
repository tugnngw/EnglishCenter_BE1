package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.GradeHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.response.HomeworkGradeHistoryResponse;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.security.UserPrincipal;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Chấm điểm bài tập cho mentor/admin (bài tự luận + bài nộp file).
 * <p>
 * Trách nhiệm:
 * <ul>
 *     <li>Phân quyền: admin chấm mọi bài; mentor chỉ chấm bài thuộc khóa học mình phụ trách;</li>
 *     <li>Tính lại {@code passed} theo điểm đạt của bài tập;</li>
 *     <li>Ghi lịch sử mỗi lần chấm/sửa điểm (người sửa, điểm cũ, điểm mới, thời gian);</li>
 *     <li>Giới hạn điểm theo điểm tối đa của bài tập (nếu có).</li>
 * </ul>
 */
public interface HomeworkGradingService {

    /**
     * Mentor chấm / sửa điểm một bài nộp.
     */
    HomeworkSubmissionResponse grade(UUID submissionId, GradeHomeworkSubmissionRequest request);

    /**
     * Lịch sử chấm của một bài nộp (mới nhất trước).
     */
    List<HomeworkGradeHistoryResponse> getHistory(UUID submissionId);

    /**
     * Quyền chấm bài:
     * <ul>
     *   <li>Mentor được GIAO chấm bài này (mentor 1-1 của học viên, snapshot lúc nộp)
     *       → chấm được;</li>
     *   <li>Mentor là NGƯỜI TẠO bài tập này (bài tập riêng của mentor, V27) → chấm được;</li>
     *   <li>Mentor đang dạy kèm 1-1 cho học viên (gói mentoring_plans còn hiệu lực)
     *       → chấm được bài của học viên đó (kể cả bài nộp trước khi mua gói).</li>
     * </ul>
     * Admin KHÔNG tham gia chấm bài tập.
     * <p>
     * <b>Chống chấm nhầm:</b> nếu bài ĐÃ được gán cho mentor khác (học viên mua
     * gói 1-1 với mentor đó) → mentor khác KHÔNG được chấm. Mỗi học viên có
     * mentor riêng, khóa học dùng chung nên không ai chấm bài của học viên khác.
     */
    void assertCanGrade(HomeworkSubmission submission, UserPrincipal principal);

    /**
     * Điểm tối đa của bài tập (đọc từ {@code content_data.maxScore}); null = không giới hạn.
     */
    BigDecimal resolveMaxScore(CourseContent homework);

    /**
     * Tính đạt/không đạt.
     * <ul>
     *     <li>Có {@code maxScore} + {@code passingScore(%)} → đạt khi
     *         {@code score >= maxScore * passingScore / 100};</li>
     *     <li>Chỉ có {@code maxScore} → đạt khi {@code score >= 50%} (quy ước mặc định);</li>
     *     <li>Không có gì → luôn đạt (không có ngưỡng).</li>
     * </ul>
     */
    Boolean resolvePassed(CourseContent homework, BigDecimal score);

    /**
     * Tính kết quả đạt/không đạt theo TỈ LỆ phần trăm.
     * <p>
     * Dùng cho bài TRẮC NGHIỆM: điểm lượt thi theo thang của ĐỀ (VD 0–30),
     * còn {@code maxScore} của bài tập có thể là thang khác (VD 0–10) → phải
     * so sánh theo phần trăm, không so trực tiếp.
     *
     * @param score     điểm đạt được (thang của đề)
     * @param maxScore  điểm tối đa của lượt thi (thang của đề); null/0 = không xác định
     * @return true/false, hoặc null khi không xác định được tỉ lệ
     */
    Boolean resolvePassedByRatio(CourseContent homework, BigDecimal score, BigDecimal maxScore);
}
