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

        // Học viên đã mua mentor 1-1 → mentor được thêm vào các khóa học học viên đang học
        // (khóa học tạo ra không có mentor sẵn) → mentor chấm bài tập của học viên trong khóa đó.
        int assignedCourses = courseMentorAssignment.assignMentorToStudentCourses(
                plan.getStudentId(), plan.getMentorId());
        if (assignedCourses > 0) {
            log.info("Assigned mentor {} to {} course(s) of student {}",
                    plan.getMentorId(), assignedCourses, plan.getStudentId());
        }

        // Học viên có thể đã nộp bài TRƯỚC khi mua gói → lúc nộp chưa có mentor,
        // bài bị gán null (AI chấm). Gán lại mentor cho các bài CHƯA CHẤM để
        // mentor thấy và chấm tay.
        int backfilled = graderAssignment.backfillUngradedSubmissions(
                plan.getStudentId(), plan.getMentorId());
        if (backfilled > 0) {
            log.info("Assigned mentor {} to {} pending submission(s) of student {} "
                    + "(student bought the plan after submitting)",
                    plan.getMentorId(), backfilled, plan.getStudentId());
        }

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
}
