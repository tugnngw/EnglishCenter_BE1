package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.common.enums.ConsultationStatus;
import com.sba301.englishcenter.common.enums.MentoringPlanStatus;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.common.enums.ScheduleStatus;
import com.sba301.englishcenter.dto.request.CreateMentoringPlanRequest;
import com.sba301.englishcenter.dto.request.PlanSessionRequest;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.dto.response.MentorResponse;
import com.sba301.englishcenter.dto.response.MentoringPlanResponse;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.entity.Booking;
import com.sba301.englishcenter.entity.ConsultationRequest;
import com.sba301.englishcenter.entity.LearningPath;
import com.sba301.englishcenter.entity.MentorSchedule;
import com.sba301.englishcenter.entity.MentoringPlan;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.BookingRepository;
import com.sba301.englishcenter.repository.ConsultationRequestRepository;
import com.sba301.englishcenter.repository.LearningPathRepository;
import com.sba301.englishcenter.repository.MentorScheduleRepository;
import com.sba301.englishcenter.repository.MentoringPlanRepository;
import com.sba301.englishcenter.repository.PaymentOrderRepository;
import com.sba301.englishcenter.service.MentorService;
import com.sba301.englishcenter.service.MentoringPlanService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.PaymentService;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentoringPlanServiceImpl implements MentoringPlanService {

    private static final BigDecimal MINUTES_PER_HOUR = BigDecimal.valueOf(60);
    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.pending, BookingStatus.confirmed);
    private static final String REJECTED_REASON = "Học viên từ chối gói";
    private static final String CANCELLED_REASON = "Mentor hủy gói";

    private final MentoringPlanRepository planRepository;
    private final MentorScheduleRepository scheduleRepository;
    private final BookingRepository bookingRepository;
    private final LearningPathRepository learningPathRepository;
    private final ConsultationRequestRepository consultationRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    // Domain Dev 1 / Dev 4 -> chỉ gọi qua service interface
    private final MentorService mentorService;
    private final UserService userService;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    private final SessionTimeValidator sessionTimeValidator;

    // ===================== mentor =====================

    @Override
    @Transactional
    public MentoringPlanResponse createPlan(UUID mentorId, CreateMentoringPlanRequest request) {
        // 1. Mentor còn hoạt động và đã có học phí theo giờ
        BigDecimal hourlyRate = getHourlyRate(mentorId);

        // 2. Chỉ tạo gói cho tài khoản học viên (Learner USER hoặc STUDENT)
        UserResponse student = userService.getUserById(request.getStudentId());
        if (!RoleName.USER.getValue().equals(student.getRole()) && !RoleName.STUDENT.getValue().equals(student.getRole())) {
            throw new AppException(ErrorCode.MENTORING_PLAN_STUDENT_INVALID);
        }

        // 3. Lộ trình có quy định số buổi thì gói phải đúng bằng số buổi đó (VD lộ trình 12 buổi)
        LearningPath path = null;
        if (request.getLearningPathId() != null) {
            path = learningPathRepository.findById(request.getLearningPathId())
                    .orElseThrow(() -> new AppException(ErrorCode.LEARNING_PATH_NOT_FOUND));
            if (path.getTotalSessions() != null && path.getTotalSessions() != request.getSessions().size()) {
                throw new AppException(ErrorCode.MENTORING_PLAN_SESSION_COUNT_INVALID,
                        String.format(ErrorCode.MENTORING_PLAN_SESSION_COUNT_INVALID.getMessage(), path.getTotalSessions()));
            }
        }

        // 4. Kiểm tra giờ từng buổi: trong tương lai, tối đa N ngày tới, độ dài hợp lệ, không trùng nhau
        List<LocalDateTime[]> times = request.getSessions().stream()
                .map(this::toUtcRange)
                .sorted(Comparator.comparing((LocalDateTime[] t) -> t[0]))
                .toList();
        LocalDateTime previousEnd = null;
        for (LocalDateTime[] t : times) {
            sessionTimeValidator.validate(t[0], t[1]);
            if (previousEnd != null && t[0].isBefore(previousEnd)) {
                throw new AppException(ErrorCode.SCHEDULE_TIME_CONFLICT, "Các buổi trong gói bị trùng giờ nhau");
            }
            previousEnd = t[1];
            // Không trùng buổi khác (chưa hủy) của mentor, không trùng buổi đang có của học viên
            if (scheduleRepository.existsByMentorIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
                    mentorId, ScheduleStatus.cancelled, t[1], t[0])) {
                throw new AppException(ErrorCode.SCHEDULE_TIME_CONFLICT,
                        "Buổi " + UtcTimeUtil.toInstant(t[0]) + " trùng với lịch dạy khác của bạn");
            }
            if (bookingRepository.existsByStudentIdAndStatusInAndSchedule_StartTimeLessThanAndSchedule_EndTimeGreaterThan(
                    request.getStudentId(), ACTIVE_STATUSES, t[1], t[0])) {
                throw new AppException(ErrorCode.SCHEDULE_TIME_CONFLICT,
                        "Buổi " + UtcTimeUtil.toInstant(t[0]) + " trùng với lịch học khác của học viên");
            }
        }

        // 5. Yêu cầu tư vấn (nếu có) phải của học viên này, chưa bị mentor khác nhận -> "agreed"
        if (request.getConsultationId() != null) {
            ConsultationRequest consultation = consultationRepository.findWithLockByRequestId(request.getConsultationId())
                    .orElseThrow(() -> new AppException(ErrorCode.CONSULTATION_NOT_FOUND));
            if (!consultation.getStudentId().equals(request.getStudentId())) {
                throw new AppException(ErrorCode.CONSULTATION_NOT_FOUND);
            }
            if (consultation.getMentorId() != null && !consultation.getMentorId().equals(mentorId)) {
                throw new AppException(ErrorCode.CONSULTATION_TAKEN);
            }
            consultation.setMentorId(mentorId);
            consultation.setStatus(ConsultationStatus.agreed);
            consultationRepository.save(consultation);
        }

        // 6. Giá mỗi buổi = hourly_rate x số giờ (số nguyên VND), tổng = tổng các buổi
        List<BigDecimal> prices = times.stream().map(t -> calculatePrice(hourlyRate, t[0], t[1])).toList();
        BigDecimal total = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) {
            throw new AppException(ErrorCode.SESSION_PRICE_INVALID);
        }

        MentoringPlan plan = planRepository.save(MentoringPlan.builder()
                .mentorId(mentorId)
                .studentId(request.getStudentId())
                .learningPathId(path != null ? path.getLearningPathId() : null)
                .consultationId(request.getConsultationId())
                .title(request.getTitle().trim())
                .note(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null)
                .totalSessions(times.size())
                .totalPrice(total)
                .status(MentoringPlanStatus.pending_payment)
                .build());

        // 7. Tạo từng buổi: giữ riêng cho học viên ("booked") + booking "pending" chờ thanh toán cả gói
        String meetingUrl = StringUtils.hasText(request.getMeetingUrl()) ? request.getMeetingUrl().trim() : null;
        for (int i = 0; i < times.size(); i++) {
            MentorSchedule schedule = scheduleRepository.save(MentorSchedule.builder()
                    .mentorId(mentorId)
                    .studentId(request.getStudentId())
                    .planId(plan.getPlanId())
                    .startTime(times.get(i)[0])
                    .endTime(times.get(i)[1])
                    .price(prices.get(i))
                    .meetingUrl(meetingUrl)
                    .status(ScheduleStatus.booked)
                    .build());
            bookingRepository.save(Booking.builder()
                    .schedule(schedule)
                    .mentorId(mentorId)
                    .studentId(request.getStudentId())
                    .status(BookingStatus.pending)
                    .build());
        }
        log.info("Mentor {} created mentoring plan {} ({} sessions) for student {}",
                mentorId, plan.getPlanId(), times.size(), request.getStudentId());

        // 8. Báo học viên vào thanh toán
        Map<UUID, String> names = new HashMap<>();
        notificationService.notify(request.getStudentId(), NotificationType.mentor_session, "Bạn có gói kèm 1-1 mới",
                "Mentor " + getFullName(mentorId, names) + " đã tạo gói \"" + plan.getTitle() + "\" gồm "
                        + times.size() + " buổi. Vào mục Mentor 1-1 để xem lịch và thanh toán.");
        return toResponse(plan, names);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MentoringPlanResponse> getMentorPlans(UUID mentorId, Pageable pageable) {
        Page<MentoringPlan> page = planRepository.findByMentorIdOrderByCreatedAtDesc(mentorId, pageable);
        Map<UUID, String> names = new HashMap<>();
        return PageResponse.<MentoringPlanResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(p -> toResponse(p, names)).toList())
                .build();
    }

    @Override
    @Transactional
    public MentoringPlanResponse cancelByMentor(UUID mentorId, UUID planId, String reason) {
        MentoringPlan plan = lockPendingPlan(planId);
        if (!plan.getMentorId().equals(mentorId)) {
            throw new AppException(ErrorCode.MENTORING_PLAN_NOT_FOUND);
        }
        cancelPlan(plan, mentorId, StringUtils.hasText(reason) ? reason.trim() : CANCELLED_REASON);
        Map<UUID, String> names = new HashMap<>();
        notificationService.notify(plan.getStudentId(), NotificationType.mentor_session, "Gói kèm 1-1 đã bị hủy",
                "Mentor " + getFullName(mentorId, names) + " đã hủy gói \"" + plan.getTitle() + "\". Lý do: "
                        + plan.getCancelReason());
        return toResponse(plan, names);
    }

    // ===================== học viên =====================

    @Override
    @Transactional(readOnly = true)
    public List<MentoringPlanResponse> getMyPlans(UUID studentId) {
        Map<UUID, String> names = new HashMap<>();
        return planRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(p -> toResponse(p, names))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MentoringPlanResponse getPlan(UUID requesterId, boolean isAdmin, UUID planId) {
        MentoringPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.MENTORING_PLAN_NOT_FOUND));
        if (!isAdmin && !plan.getMentorId().equals(requesterId) && !plan.getStudentId().equals(requesterId)) {
            throw new AppException(ErrorCode.MENTORING_PLAN_NOT_FOUND);
        }
        return toResponse(plan, new HashMap<>());
    }

    @Override
    @Transactional
    public PaymentOrderResponse checkout(UUID studentId, UUID planId) {
        MentoringPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.MENTORING_PLAN_NOT_FOUND));
        if (!plan.getStudentId().equals(studentId)) {
            throw new AppException(ErrorCode.MENTORING_PLAN_NOT_FOUND);
        }
        if (plan.getStatus() != MentoringPlanStatus.pending_payment) {
            throw new AppException(ErrorCode.MENTORING_PLAN_STATUS_INVALID);
        }
        // Buổi đầu tiên đã qua giờ thì không thanh toán được nữa, mentor phải xếp lại
        List<MentorSchedule> schedules = scheduleRepository.findByPlanIdOrderByStartTimeAsc(planId);
        if (schedules.isEmpty() || !schedules.get(0).getStartTime().isAfter(UtcTimeUtil.nowUtc())) {
            throw new AppException(ErrorCode.MENTORING_PLAN_EXPIRED);
        }
        // Đã có đơn đang chờ thì trả lại đơn đó (PaymentService tự xử lý)
        String mentorName = userService.getUserById(plan.getMentorId()).getFullName();
        return paymentService.createMentorPlanOrder(studentId, planId, plan.getTotalPrice(),
                "Gói kèm 1-1 \"" + plan.getTitle() + "\" với mentor " + mentorName);
    }

    @Override
    @Transactional
    public MentoringPlanResponse rejectByStudent(UUID studentId, UUID planId, String reason) {
        MentoringPlan plan = lockPendingPlan(planId);
        if (!plan.getStudentId().equals(studentId)) {
            throw new AppException(ErrorCode.MENTORING_PLAN_NOT_FOUND);
        }
        cancelPlan(plan, studentId, StringUtils.hasText(reason) ? reason.trim() : REJECTED_REASON);
        Map<UUID, String> names = new HashMap<>();
        notificationService.notify(plan.getMentorId(), NotificationType.mentor_session, "Học viên từ chối gói kèm 1-1",
                "Học viên " + getFullName(studentId, names) + " đã từ chối gói \"" + plan.getTitle() + "\". Lý do: "
                        + plan.getCancelReason());
        return toResponse(plan, names);
    }

    // ===================== helpers =====================

    // Hủy đơn SePay đang chờ trước (cùng thứ tự khóa với webhook: đơn -> gói), rồi khóa gói
    private MentoringPlan lockPendingPlan(UUID planId) {
        paymentService.cancelPendingPlanOrder(planId);
        MentoringPlan plan = planRepository.findWithLockByPlanId(planId)
                .orElseThrow(() -> new AppException(ErrorCode.MENTORING_PLAN_NOT_FOUND));
        if (plan.getStatus() != MentoringPlanStatus.pending_payment) {
            throw new AppException(ErrorCode.MENTORING_PLAN_STATUS_INVALID);
        }
        return plan;
    }

    // Gói chưa thanh toán bị hủy: đóng mọi buổi và booking của gói
    private void cancelPlan(MentoringPlan plan, UUID cancelledBy, String reason) {
        LocalDateTime now = UtcTimeUtil.nowUtc();
        for (Booking booking : bookingRepository.findBySchedule_PlanIdOrderBySchedule_StartTimeAsc(plan.getPlanId())) {
            if (ACTIVE_STATUSES.contains(booking.getStatus())) {
                booking.setStatus(BookingStatus.cancelled);
                booking.setCancelReason(reason);
                booking.setCancelledBy(cancelledBy);
                booking.setCancelledAt(now);
                bookingRepository.save(booking);
            }
            MentorSchedule schedule = booking.getSchedule();
            schedule.setStatus(ScheduleStatus.cancelled);
            scheduleRepository.save(schedule);
        }
        plan.setStatus(MentoringPlanStatus.cancelled);
        plan.setCancelReason(reason);
        planRepository.save(plan);
        reopenConsultation(plan);
        log.info("Mentoring plan {} cancelled by {}: {}", plan.getPlanId(), cancelledBy, reason);
    }

    // Gói bị hủy / học viên từ chối -> lộ trình chưa chốt nữa: yêu cầu tư vấn "agreed" quay về "contacted"
    // (mentor vẫn phụ trách, có thể tư vấn lại rồi tạo gói mới hoặc đóng yêu cầu)
    private void reopenConsultation(MentoringPlan plan) {
        if (plan.getConsultationId() == null) {
            return;
        }
        consultationRepository.findWithLockByRequestId(plan.getConsultationId())
                .filter(c -> c.getStatus() == ConsultationStatus.agreed)
                .ifPresent(c -> {
                    c.setStatus(ConsultationStatus.contacted);
                    consultationRepository.save(c);
                });
    }

    private LocalDateTime[] toUtcRange(PlanSessionRequest session) {
        return new LocalDateTime[]{UtcTimeUtil.toUtc(session.getStartTime()), UtcTimeUtil.toUtc(session.getEndTime())};
    }

    private BigDecimal getHourlyRate(UUID mentorId) {
        MentorResponse mentor = mentorService.getMentorById(mentorId);
        if (mentor.getHourlyRate() == null) {
            throw new AppException(ErrorCode.MENTOR_HOURLY_RATE_NOT_SET);
        }
        return mentor.getHourlyRate();
    }

    // Giá = hourly_rate x (số phút / 60), làm tròn tới đồng (SePay chỉ nhận số tiền nguyên VND)
    private BigDecimal calculatePrice(BigDecimal hourlyRate, LocalDateTime startTime, LocalDateTime endTime) {
        long minutes = Duration.between(startTime, endTime).toMinutes();
        return hourlyRate.multiply(BigDecimal.valueOf(minutes))
                .divide(MINUTES_PER_HOUR, 0, RoundingMode.HALF_UP)
                .setScale(2);
    }

    private String getFullName(UUID userId, Map<UUID, String> names) {
        return names.computeIfAbsent(userId, id -> userService.getUserById(id).getFullName());
    }

    private MentoringPlanResponse toResponse(MentoringPlan plan, Map<UUID, String> names) {
        List<BookingResponse> sessions = new ArrayList<>();
        int completed = 0;
        for (Booking booking : bookingRepository.findBySchedule_PlanIdOrderBySchedule_StartTimeAsc(plan.getPlanId())) {
            if (booking.getStatus() == BookingStatus.completed) {
                completed++;
            }
            MentorSchedule schedule = booking.getSchedule();
            sessions.add(BookingResponse.builder()
                    .bookingId(booking.getBookingId())
                    .scheduleId(schedule.getScheduleId())
                    .planId(plan.getPlanId())
                    .mentorId(booking.getMentorId())
                    .mentorName(getFullName(booking.getMentorId(), names))
                    .studentId(booking.getStudentId())
                    .studentName(getFullName(booking.getStudentId(), names))
                    .startTime(UtcTimeUtil.toInstant(schedule.getStartTime()))
                    .endTime(UtcTimeUtil.toInstant(schedule.getEndTime()))
                    .price(schedule.getPrice())
                    // Chưa thanh toán thì chưa lộ link phòng học
                    .meetingUrl(plan.getStatus() == MentoringPlanStatus.pending_payment ? null : schedule.getMeetingUrl())
                    .status(booking.getStatus().name())
                    .cancelReason(booking.getCancelReason())
                    .cancelledAt(UtcTimeUtil.toInstant(booking.getCancelledAt()))
                    .createdAt(UtcTimeUtil.fromServerTime(booking.getCreatedAt()))
                    .build());
        }
        String pathName = plan.getLearningPathId() == null ? null
                : learningPathRepository.findById(plan.getLearningPathId()).map(LearningPath::getName).orElse(null);
        String orderCode = plan.getStatus() != MentoringPlanStatus.pending_payment ? null
                : paymentOrderRepository.findFirstByPlanIdAndStatus(plan.getPlanId(), PaymentOrderStatus.PENDING)
                        .map(order -> order.getOrderCode()).orElse(null);
        return MentoringPlanResponse.builder()
                .planId(plan.getPlanId())
                .mentorId(plan.getMentorId())
                .mentorName(getFullName(plan.getMentorId(), names))
                .studentId(plan.getStudentId())
                .studentName(getFullName(plan.getStudentId(), names))
                .learningPathId(plan.getLearningPathId())
                .learningPathName(pathName)
                .consultationId(plan.getConsultationId())
                .title(plan.getTitle())
                .note(plan.getNote())
                .totalSessions(plan.getTotalSessions())
                .completedSessions(completed)
                .totalPrice(plan.getTotalPrice())
                .status(plan.getStatus().name())
                .cancelReason(plan.getCancelReason())
                .paidAt(UtcTimeUtil.toInstant(plan.getPaidAt()))
                .createdAt(UtcTimeUtil.fromServerTime(plan.getCreatedAt()))
                .paymentOrderCode(orderCode)
                .sessions(sessions)
                .build();
    }
}
