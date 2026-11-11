package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.entity.Exam;

import java.util.UUID;

/**
 * Luồng chấm bài thi với gói Mentor 1-1 (V30).
 * <p>
 * <b>Quy tắc nghiệp vụ:</b>
 * <ol>
 *   <li>Đề của khóa học có câu tự luận (Writing / Speaking) → <b>BẮT BUỘC</b> có mentor chấm tay.</li>
 *   <li>Học viên <b>ĐÃ mua</b> gói mentor → khi nộp, bài vào thẳng hàng đợi chấm của mentor
 *       (trạng thái {@code submitted}).</li>
 *   <li>Học viên <b>CHƯA mua</b> gói mentor (tự học) → KHÔNG bị chặn làm bài. Học viên làm và
 *       nộp trọn vẹn; phần trắc nghiệm chấm tự động trả điểm ngay, phần tự luận được
 *       <b>lưu nháp</b> ở trạng thái {@code awaiting_mentor} chờ nâng cấp gói — không phải làm lại.</li>
 *   <li>Học viên mua gói mentor <b>SAU</b> khi đã nộp → mọi lượt thi đang {@code awaiting_mentor}
 *       được chuyển sang {@code submitted} để mentor thấy và chấm tay.</li>
 * </ol>
 * <p>
 * Đề thi thử ({@code trial}) và đề luyện tập AI ({@code practice}) KHÔNG dùng luồng này:
 * đề thử do mọi mentor chấm, đề luyện tập do AI chấm.
 */
public interface ExamMentorAssignmentService {

    /**
     * Trạng thái của lượt thi sau khi học viên nộp.
     * <p>
     * Thứ tự ưu tiên:
     * <ol>
     *   <li>Còn câu tự luận chưa chấm + đề khóa học + học viên CHƯA mua gói mentor
     *       → {@code awaiting_mentor} (lưu nháp, chờ nâng cấp gói);</li>
     *   <li>Hệ thống tự nộp do hết giờ → {@code expired} (đã có câu tự luận) hoặc {@code graded};</li>
     *   <li>Còn câu tự luận chờ mentor chấm → {@code submitted};</li>
     *   <li>Chấm xong hết → {@code graded}.</li>
     * </ol>
     * Trường hợp hết giờ mà học viên chưa mua gói vẫn là {@code awaiting_mentor} để
     * bài tự luận được bàn giao cho mentor khi học viên nâng cấp gói (không mất bài).
     *
     * @param exam          đề đang thi
     * @param pendingManual còn câu tự luận chưa được chấm tay
     * @param studentId     học viên vừa nộp
     * @param auto          hệ thống tự nộp do hết giờ
     */
    AttemptStatus resolveSubmitStatus(Exam exam, boolean pendingManual, UUID studentId, boolean auto);

    /** Học viên đã mua gói mentor 1-1 (đã thanh toán) hay chưa. */
    boolean hasMentorPlan(UUID studentId);

    /**
     * Chuyển toàn bộ lượt thi đang LƯU NHÁP của học viên sang hàng đợi chấm của mentor.
     * <p>
     * Gọi khi học viên thanh toán thành công gói mentor. Học viên KHÔNG phải làm lại bài —
     * bài tự luận đã làm vẫn giữ nguyên, chỉ chuyển trạng thái để mentor nhận chấm.
     *
     * @param studentId học viên vừa mua gói
     * @param mentorId  mentor 1-1 của học viên
     * @return số lượt thi đã chuyển sang hàng đợi chấm
     */
    int backfillAwaitingMentorAttempts(UUID studentId, UUID mentorId);
}
