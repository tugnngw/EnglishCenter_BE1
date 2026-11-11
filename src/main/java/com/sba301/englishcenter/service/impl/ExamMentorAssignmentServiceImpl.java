package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.service.ExamMentorAssignmentService;
import com.sba301.englishcenter.service.HomeworkGraderAssignmentService;
import com.sba301.englishcenter.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Triển khai {@link ExamMentorAssignmentService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExamMentorAssignmentServiceImpl implements ExamMentorAssignmentService {

    private final ExamAttemptRepository attemptRepository;
    /** Tra cứu mentor 1-1 của học viên (gói đã thanh toán). */
    private final HomeworkGraderAssignmentService graderAssignment;
    private final NotificationService notificationService;

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
    @Override
    public AttemptStatus resolveSubmitStatus(Exam exam, boolean pendingManual, UUID studentId, boolean auto) {
        if (pendingManual && exam != null && exam.getExamScope() == ExamScope.COURSE && !hasMentorPlan(studentId)) {
            // Tự học: giữ bài tự luận ở trạng thái nháp chờ nâng cấp gói mentor.
            return AttemptStatus.AWAITING_MENTOR;
        }
        if (auto) {
            return AttemptStatus.EXPIRED;
        }
        return pendingManual ? AttemptStatus.SUBMITTED : AttemptStatus.GRADED;
    }

    /** Học viên đã mua gói mentor 1-1 (đã thanh toán) hay chưa. */
    @Override
    @Transactional(readOnly = true)
    public boolean hasMentorPlan(UUID studentId) {
        return studentId != null && graderAssignment.hasMentor(studentId);
    }

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
    @Override
    @Transactional
    public int backfillAwaitingMentorAttempts(UUID studentId, UUID mentorId) {
        if (studentId == null || mentorId == null) {
            return 0;
        }
        List<ExamAttempt> drafts = attemptRepository.findByStudentAndStatus(studentId, AttemptStatus.AWAITING_MENTOR);
        int updated = 0;
        for (ExamAttempt attempt : drafts) {
            attempt.setStatus(AttemptStatus.SUBMITTED);
            // Mentor chưa nhận chấm → bỏ claim cũ (nếu có) để mentor mới nhận được.
            attempt.setGradingBy(null);
            attempt.setGradingClaimedAt(null);
            attemptRepository.save(attempt);
            updated++;
        }
        if (updated > 0) {
            log.info("Đã chuyển {} lượt thi lưu nháp của học viên {} sang hàng đợi chấm của mentor {} "
                    + "(học viên mua gói mentor sau khi nộp bài)", updated, studentId, mentorId);
            notifyBackfilled(studentId, mentorId, updated);
        }
        return updated;
    }

    /** Báo cho học viên và mentor biết các bài đã vào hàng đợi chấm. */
    private void notifyBackfilled(UUID studentId, UUID mentorId, int count) {
        try {
            notificationService.notify(studentId, NotificationType.exam_result, "Bài tự luận đã được chuyển cho mentor",
                    count + " bài thi tự luận bạn đã nộp trước đó đã được chuyển cho mentor chấm tay. "
                            + "Bạn không cần làm lại bài.");
        } catch (RuntimeException ex) {
            log.warn("Không gửi được thông báo quay lại hàng đợi chấm cho học viên {}: {}", studentId, ex.getMessage());
        }
        try {
            notificationService.notify(mentorId, NotificationType.exam_result, "Có bài thi mới cần chấm",
                    count + " bài thi tự luận của học viên đã mua gói kèm 1-1 với bạn đang chờ chấm.");
        } catch (RuntimeException ex) {
            log.warn("Không gửi được thông báo bài chờ chấm cho mentor {}: {}", mentorId, ex.getMessage());
        }
    }
}
