package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.common.enums.MentoringPlanStatus;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.entity.Booking;
import com.sba301.englishcenter.entity.MentoringPlan;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.BookingRepository;
import com.sba301.englishcenter.repository.MentoringPlanRepository;
import com.sba301.englishcenter.service.CourseMentorAssignmentService;
import com.sba301.englishcenter.service.ExamMentorAssignmentService;
import com.sba301.englishcenter.service.HomeworkGraderAssignmentService;
import com.sba301.englishcenter.service.MentoringPlanPaymentService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Xử lý gói kèm 1-1 theo kết quả thanh toán SePay (đơn MENTOR_PLAN).
 * Không phụ thuộc PaymentService để tránh vòng phụ thuộc: MentoringPlanServiceImpl -> PaymentService -> class này.
 * Đơn hết hạn / bị hủy thì gói vẫn giữ "pending_payment" để học viên thanh toán lại (không cần làm gì).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MentoringPlanPaymentServiceImpl implements MentoringPlanPaymentService {

    private final MentoringPlanRepository planRepository;
    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;
    private final UserService userService;
    private final CourseMentorAssignmentService courseMentorAssignment;
    private final HomeworkGraderAssignmentService graderAssignment;
    private final ExamMentorAssignmentService examMentorAssignment;

    @Override
    @Transactional
    public UUID confirmPaidPlan(UUID planId) {
        MentoringPlan plan = planRepository.findWithLockByPlanId(planId)
                .orElseThrow(() -> new AppException(ErrorCode.MENTORING_PLAN_NOT_FOUND));
        if (plan.getStatus() != MentoringPlanStatus.pending_payment) {
            log.warn("Mentoring plan {} is {} when payment arrived -> review", planId, plan.getStatus());
            return null;
        }
        List<Booking> bookings = bookingRepository.findBySchedule_PlanIdOrderBySchedule_StartTimeAsc(planId);
        if (bookings.isEmpty()) {
            // Gói không có buổi nào → vẫn kích hoạt gói để học viên có mentor 1-1
            // (bài thi / bài tập đã nộp trước đó vẫn phải được bàn giao cho mentor).
            plan.setStatus(MentoringPlanStatus.active);
            plan.setPaidAt(UtcTimeUtil.nowUtc());
            planRepository.save(plan);
            backfillAfterPayment(plan);
            return null;
        }

        // Gói -> active, mọi buổi đang chờ -> confirmed
        plan.setStatus(MentoringPlanStatus.active);
        plan.setPaidAt(UtcTimeUtil.nowUtc());
        planRepository.save(plan);
        for (Booking booking : bookings) {
            if (booking.getStatus() == BookingStatus.pending) {
                booking.setStatus(BookingStatus.confirmed);
                bookingRepository.save(booking);
            }
        }
        log.info("Mentoring plan {} activated after SePay payment", planId);

        backfillAfterPayment(plan);

        String studentName = userService.getUserById(plan.getStudentId()).getFullName();
        String mentorName = userService.getUserById(plan.getMentorId()).getFullName();
        notificationService.notify(plan.getStudentId(), NotificationType.mentor_session, "Thanh toán gói kèm 1-1 thành công",
                "Đã nhận thanh toán. " + plan.getTotalSessions() + " buổi kèm 1-1 với mentor " + mentorName
                        + " đã được xác nhận, xem lịch trong mục Mentor 1-1.");
        notificationService.notify(plan.getMentorId(), NotificationType.mentor_session, "Học viên đã thanh toán gói kèm 1-1",
                "Học viên " + studentName + " đã thanh toán gói \"" + plan.getTitle() + "\" ("
                        + plan.getTotalSessions() + " buổi).");
        return bookings.get(0).getSchedule().getScheduleId();
    }

    /**
     * Học viên đã mua gói mentor 1-1 → bàn giao các bài đã nộp TRƯỚC khi mua gói:
     * <ul>
     *   <li>Bài tập (homework): lúc nộp chưa có mentor → AI chấm. Gán lại mentor cho các
     *       bài CHƯA CHẤM để mentor thấy và chấm tay.</li>
     *   <li>Bài thi (V30): phần tự luận lưu nháp ở trạng thái {@code awaiting_mentor} →
     *       chuyển sang {@code submitted} để vào hàng đợi chấm. Học viên KHÔNG phải làm lại bài.</li>
     * </ul>
     */
    private void backfillAfterPayment(MentoringPlan plan) {
        UUID studentId = plan.getStudentId();
        UUID mentorId = plan.getMentorId();

        // Học viên đã mua mentor 1-1 → mentor được thêm vào các khóa học học viên đang học
        // (khóa học tạo ra không có mentor sẵn) → mentor chấm bài tập của học viên trong khóa đó.
        int assignedCourses = courseMentorAssignment.assignMentorToStudentCourses(studentId, mentorId);
        if (assignedCourses > 0) {
            log.info("Assigned mentor {} to {} course(s) of student {}",
                    mentorId, assignedCourses, studentId);
        }

        // Học viên có thể đã nộp bài TRƯỚC khi mua gói → lúc nộp chưa có mentor,
        // bài bị gán null (AI chấm). Gán lại mentor cho các bài CHƯA CHẤM để
        // mentor thấy và chấm tay.
        int backfilled = graderAssignment.backfillUngradedSubmissions(studentId, mentorId);
        if (backfilled > 0) {
            log.info("Assigned mentor {} to {} pending submission(s) of student {} "
                    + "(student bought the plan after submitting)",
                    mentorId, backfilled, studentId);
        }

        // V30: học viên có thể đã nộp BÀI THI (đề cuối kỳ) trước khi mua gói →
        // phần tự luận được lưu nháp ở trạng thái 'awaiting_mentor'. Chuyển sang
        // 'submitted' để mentor thấy và chấm tay. Học viên KHÔNG phải làm lại bài.
        int backfilledAttempts = examMentorAssignment.backfillAwaitingMentorAttempts(studentId, mentorId);
        if (backfilledAttempts > 0) {
            log.info("Moved {} awaiting-mentor attempt(s) of student {} to mentor {} grading queue",
                    backfilledAttempts, studentId, mentorId);
        }
    }
}
