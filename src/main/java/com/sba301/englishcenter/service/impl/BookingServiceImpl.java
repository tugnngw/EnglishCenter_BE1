package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.common.enums.ScheduleStatus;
import com.sba301.englishcenter.dto.request.BookScheduleRequest;
import com.sba301.englishcenter.dto.request.CancelBookingRequest;
import com.sba301.englishcenter.dto.request.RescheduleBookingRequest;
import com.sba301.englishcenter.dto.request.UpdateBookingStatusRequest;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.entity.Booking;
import com.sba301.englishcenter.entity.MentorSchedule;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.BookingRepository;
import com.sba301.englishcenter.repository.MentorScheduleRepository;
import com.sba301.englishcenter.repository.PaymentOrderRepository;
import com.sba301.englishcenter.service.BookingService;
import com.sba301.englishcenter.service.MentorService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.service.PaymentService;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.specification.BookingSpecification;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    // Booking còn hiệu lực: được hủy / đổi lịch, dùng để kiểm tra trùng giờ
    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.pending, BookingStatus.confirmed);
    // Lý do ghi vào booking cũ khi học viên đổi lịch
    private static final String RESCHEDULE_REASON = "Đổi lịch";
    private static final String SORT_FIELD = "schedule.startTime";

    private final BookingRepository bookingRepository;
    private final MentorScheduleRepository mentorScheduleRepository;
    // Hồ sơ mentor / người dùng thuộc domain Dev 1 -> chỉ gọi qua service interface
    private final MentorService mentorService;
    private final UserService userService;
    private final NotificationService notificationService;
    // Thanh toán SePay của Dev 4: tạo / hủy đơn MENTOR_SESSION cho booking
    private final PaymentService paymentService;
    private final PaymentOrderRepository paymentOrderRepository;

    // Phải hủy / đổi lịch trước giờ học ít nhất bao nhiêu giờ (application.properties)
    @Value("${app.booking.cancel-deadline-hours:24}")
    private long cancelDeadlineHours;

    // Nhắc trước giờ học bao nhiêu phút (USER_FLOWS 1.6: 30 phút)
    @Value("${app.booking.reminder-minutes-before:30}")
    private long reminderMinutesBefore;

    @Override
    @Transactional
    public BookingResponse book(UUID studentId, BookScheduleRequest request) {
        // 1. Khóa dòng slot, slot phải còn trống và chưa tới giờ (TASK D5-07)
        MentorSchedule schedule = lockAvailableSlot(request.getScheduleId());

        // 2. Mentor của slot phải còn hoạt động (chưa bị Admin thu hồi/khóa)
        mentorService.getMentorById(schedule.getMentorId());

        // 3. Học viên không được có 2 buổi trùng giờ nhau (TASK D5-08)
        boolean studentBusy = bookingRepository
                .existsByStudentIdAndStatusInAndSchedule_StartTimeLessThanAndSchedule_EndTimeGreaterThan(
                        studentId, ACTIVE_STATUSES, schedule.getEndTime(), schedule.getStartTime());
        if (studentBusy) {
            throw new AppException(ErrorCode.SCHEDULE_TIME_CONFLICT);
        }

        // 4. Giá slot phải là số nguyên VND > 0 để thanh toán qua SePay
        BigDecimal price = schedule.getPrice();
        if (price == null || price.signum() <= 0 || price.stripTrailingZeros().scale() > 0) {
            throw new AppException(ErrorCode.SESSION_PRICE_INVALID);
        }

        // 5. Tạo booking "pending" và giữ slot ("booked") trong thời gian chờ thanh toán
        Booking booking = bookingRepository.save(Booking.builder()
                .schedule(schedule)
                .mentorId(schedule.getMentorId())
                .studentId(studentId)
                .status(BookingStatus.pending)
                .build());
        schedule.setStatus(ScheduleStatus.booked);
        mentorScheduleRepository.save(schedule);

        // 6. Tạo đơn SePay (Dev 4). Thanh toán xong webhook chuyển booking sang "confirmed" và gửi thông báo;
        //    đơn hết hạn / bị hủy thì booking bị hủy và slot mở lại (BookingPaymentServiceImpl)
        Map<UUID, String> names = new HashMap<>();
        PaymentOrderResponse order = paymentService.createMentorSessionOrder(studentId, booking.getBookingId(), price,
                "Buổi kèm 1-1 với mentor " + getFullName(booking.getMentorId(), names));
        log.info("Student {} booked schedule {} -> booking {} waiting for payment {}",
                studentId, schedule.getScheduleId(), booking.getBookingId(), order.orderCode());

        return toResponse(booking, names, order.orderCode());
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBooking(UUID requesterId, boolean isAdmin, UUID bookingId) {
        Booking booking = findBooking(bookingId);
        // Không phải người trong booking -> coi như không tồn tại
        if (!isAdmin && !isParticipant(booking, requesterId)) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        return toResponse(booking, new HashMap<>());
    }

    @Override
    @Transactional
    public BookingResponse cancel(UUID requesterId, UUID bookingId, CancelBookingRequest request) {
        Booking booking = findBooking(bookingId);
        if (!isParticipant(booking, requesterId)) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }

        // Chỉ hủy được booking còn hiệu lực và còn trong thời hạn (TASK D5-09)
        validateActive(booking);
        validateCancelDeadline(booking);

        return cancelAndNotify(booking, requesterId, getReason(request));
    }

    @Override
    @Transactional
    public BookingResponse cancelByAdmin(UUID adminId, UUID bookingId, CancelBookingRequest request) {
        Booking booking = findBooking(bookingId);
        // Admin xử lý booking bất thường: không áp dụng thời hạn hủy
        validateActive(booking);
        return cancelAndNotify(booking, adminId, getReason(request));
    }

    @Override
    @Transactional
    public BookingResponse reschedule(UUID studentId, UUID bookingId, RescheduleBookingRequest request) {
        // 1. Booking cũ phải của học viên này, còn hiệu lực và còn trong thời hạn đổi
        Booking oldBooking = findBooking(bookingId);
        if (!oldBooking.getStudentId().equals(studentId)) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        validateActive(oldBooking);
        // Chỉ đổi lịch buổi đã thanh toán; booking chờ thanh toán thì hủy rồi đặt lại
        if (oldBooking.getStatus() != BookingStatus.confirmed) {
            throw new AppException(ErrorCode.BOOKING_PAYMENT_PENDING);
        }
        validateCancelDeadline(oldBooking);

        // 2. Khóa slot mới: còn trống, chưa tới giờ và cùng mentor
        MentorSchedule newSchedule = lockAvailableSlot(request.getNewScheduleId());
        if (!newSchedule.getMentorId().equals(oldBooking.getMentorId())) {
            throw new AppException(ErrorCode.RESCHEDULE_MENTOR_MISMATCH);
        }
        // Giữ nguyên khoản đã thanh toán nên slot mới phải cùng giá
        if (newSchedule.getPrice() == null
                || newSchedule.getPrice().compareTo(oldBooking.getSchedule().getPrice()) != 0) {
            throw new AppException(ErrorCode.RESCHEDULE_PRICE_MISMATCH);
        }
        mentorService.getMentorById(newSchedule.getMentorId());

        // 3. Slot mới không trùng giờ với booking khác của học viên (bỏ qua booking đang đổi)
        boolean studentBusy = bookingRepository
                .existsByStudentIdAndBookingIdNotAndStatusInAndSchedule_StartTimeLessThanAndSchedule_EndTimeGreaterThan(
                        studentId, bookingId, ACTIVE_STATUSES, newSchedule.getEndTime(), newSchedule.getStartTime());
        if (studentBusy) {
            throw new AppException(ErrorCode.SCHEDULE_TIME_CONFLICT);
        }

        // 4. Hủy booking cũ, mở lại slot cũ
        MentorSchedule oldSchedule = lockSlot(oldBooking.getSchedule().getScheduleId());
        markCancelled(oldBooking, studentId, RESCHEDULE_REASON);
        oldSchedule.setStatus(ScheduleStatus.available);
        bookingRepository.save(oldBooking);
        mentorScheduleRepository.save(oldSchedule);

        // 5. Đặt slot mới, ghi lại booking cũ để truy vết
        Booking newBooking = bookingRepository.save(Booking.builder()
                .schedule(newSchedule)
                .mentorId(newSchedule.getMentorId())
                .studentId(studentId)
                .status(BookingStatus.confirmed)
                .rescheduledFromId(oldBooking.getBookingId())
                .build());
        newSchedule.setStatus(ScheduleStatus.booked);
        mentorScheduleRepository.save(newSchedule);
        log.info("Student {} rescheduled booking {} -> {}", studentId, bookingId, newBooking.getBookingId());

        // 6. Thông báo cho học viên và mentor (TASK D5-16)
        Map<UUID, String> names = new HashMap<>();
        String message = "Học viên " + getFullName(studentId, names) + " đã đổi buổi kèm 1-1 với mentor "
                + getFullName(newBooking.getMentorId(), names) + " sang khung giờ khác.";
        notifyBoth(newBooking, "Lịch hẹn đã được đổi", message);

        return toResponse(newBooking, names);
    }

    @Override
    @Transactional
    public BookingResponse updateStatusByMentor(UUID mentorId, UUID bookingId, UpdateBookingStatusRequest request) {
        // 1. Mentor chỉ được ghi nhận "completed" hoặc "no_show"
        if (request.getStatus() != BookingStatus.completed && request.getStatus() != BookingStatus.no_show) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID);
        }

        // 2. Booking phải của mentor này và còn hiệu lực
        Booking booking = findBooking(bookingId);
        if (!booking.getMentorId().equals(mentorId)) {
            throw new AppException(ErrorCode.BOOKING_NOT_FOUND);
        }
        // Chỉ booking đã thanh toán (confirmed) mới có buổi học để ghi nhận kết quả
        if (booking.getStatus() != BookingStatus.confirmed) {
            throw new AppException(ErrorCode.BOOKING_STATUS_INVALID);
        }

        // 3. Chỉ cập nhật được khi buổi học đã bắt đầu
        MentorSchedule schedule = booking.getSchedule();
        if (schedule.getStartTime().isAfter(UtcTimeUtil.nowUtc())) {
            throw new AppException(ErrorCode.BOOKING_NOT_STARTED);
        }

        booking.setStatus(request.getStatus());
        schedule.setStatus(ScheduleStatus.completed);
        bookingRepository.save(booking);
        mentorScheduleRepository.save(schedule);
        log.info("Mentor {} set booking {} -> {}", mentorId, bookingId, request.getStatus());

        return toResponse(booking, new HashMap<>());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getStudentSchedule(UUID studentId, boolean past, Pageable pageable) {
        return findPage(BookingSpecification.forStudent(studentId, past, UtcTimeUtil.nowUtc()), pageable, past);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getMentorSchedule(UUID mentorId, boolean past, Pageable pageable) {
        return findPage(BookingSpecification.forMentor(mentorId, past, UtcTimeUtil.nowUtc()), pageable, past);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> getAllBookings(BookingStatus status, UUID mentorId, UUID studentId,
                                                        Pageable pageable) {
        // Admin xem mới nhất trước
        return findPage(BookingSpecification.filterForAdmin(status, mentorId, studentId), pageable, true);
    }

    @Override
    @Transactional
    public int sendUpcomingReminders() {
        LocalDateTime now = UtcTimeUtil.nowUtc();
        // Chỉ booking đã thanh toán (confirmed), chưa nhắc, sắp bắt đầu trong N phút tới
        List<Booking> bookings = bookingRepository
                .findByStatusAndReminderSentAtIsNullAndSchedule_StartTimeGreaterThanAndSchedule_StartTimeLessThanEqual(
                        BookingStatus.confirmed, now, now.plusMinutes(reminderMinutesBefore));

        Map<UUID, String> names = new HashMap<>();
        for (Booking booking : bookings) {
            MentorSchedule schedule = booking.getSchedule();
            long minutesLeft = Math.max(1, Duration.between(now, schedule.getStartTime()).toMinutes());
            notifyBoth(booking, "Sắp đến giờ học",
                    "Buổi kèm 1-1 giữa học viên " + getFullName(booking.getStudentId(), names)
                            + " và mentor " + getFullName(booking.getMentorId(), names)
                            + " bắt đầu sau khoảng " + minutesLeft + " phút. Vào phòng học: "
                            + (schedule.getMeetingUrl() != null ? schedule.getMeetingUrl() : "xem trong Lịch của tôi") + ".");
            booking.setReminderSentAt(now);
            bookingRepository.save(booking);
        }
        if (!bookings.isEmpty()) {
            log.info("Sent upcoming-session reminders for {} booking(s)", bookings.size());
        }
        return bookings.size();
    }

    // ===================== helpers =====================

    private Booking findBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.BOOKING_NOT_FOUND));
    }

    private MentorSchedule lockSlot(UUID scheduleId) {
        return mentorScheduleRepository.findWithLockByScheduleId(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    // Khóa dòng slot rồi kiểm tra còn trống và chưa tới giờ, tránh 2 học viên đặt cùng 1 slot cùng lúc
    private MentorSchedule lockAvailableSlot(UUID scheduleId) {
        MentorSchedule schedule = lockSlot(scheduleId);
        if (schedule.getStatus() != ScheduleStatus.available
                || !schedule.getStartTime().isAfter(UtcTimeUtil.nowUtc())) {
            throw new AppException(ErrorCode.SCHEDULE_NOT_AVAILABLE);
        }
        return schedule;
    }

    private boolean isParticipant(Booking booking, UUID userId) {
        return booking.getStudentId().equals(userId) || booking.getMentorId().equals(userId);
    }

    private void validateActive(Booking booking) {
        if (!ACTIVE_STATUSES.contains(booking.getStatus())) {
            throw new AppException(ErrorCode.BOOKING_NOT_CANCELLABLE);
        }
    }

    // Giờ bắt đầu phải cách hiện tại ít nhất cancelDeadlineHours giờ
    private void validateCancelDeadline(Booking booking) {
        LocalDateTime deadline = booking.getSchedule().getStartTime().minusHours(cancelDeadlineHours);
        if (UtcTimeUtil.nowUtc().isAfter(deadline)) {
            throw new AppException(ErrorCode.BOOKING_CANCEL_DEADLINE_PASSED);
        }
    }

    private String getReason(CancelBookingRequest request) {
        return request == null ? null : request.getReason();
    }

    private void markCancelled(Booking booking, UUID cancelledBy, String reason) {
        booking.setStatus(BookingStatus.cancelled);
        booking.setCancelReason(reason);
        booking.setCancelledBy(cancelledBy);
        booking.setCancelledAt(UtcTimeUtil.nowUtc());
    }

    // Hủy booking, mở lại slot cho người khác đặt, thông báo cho học viên và mentor
    private BookingResponse cancelAndNotify(Booking booking, UUID cancelledBy, String reason) {
        // Booking chưa thanh toán: hủy luôn đơn SePay đang chờ (khóa đơn trước slot, cùng thứ tự với webhook).
        // Booking đã thanh toán: chỉ hủy lịch, không hoàn tiền tự động
        if (booking.getStatus() == BookingStatus.pending) {
            paymentService.cancelPendingBookingOrder(booking.getBookingId());
        }
        MentorSchedule schedule = lockSlot(booking.getSchedule().getScheduleId());
        markCancelled(booking, cancelledBy, reason);
        schedule.setStatus(ScheduleStatus.available);
        bookingRepository.save(booking);
        mentorScheduleRepository.save(schedule);
        log.info("User {} cancelled booking {}", cancelledBy, booking.getBookingId());

        Map<UUID, String> names = new HashMap<>();
        String message = "Buổi kèm 1-1 giữa học viên " + getFullName(booking.getStudentId(), names)
                + " và mentor " + getFullName(booking.getMentorId(), names) + " đã bị hủy.";
        if (reason != null && !reason.isBlank()) {
            message += " Lý do: " + reason;
        }
        notifyBoth(booking, "Lịch hẹn đã bị hủy", message);

        return toResponse(booking, names);
    }

    private void notifyBoth(Booking booking, String title, String message) {
        UUID scheduleId = booking.getSchedule().getScheduleId();
        notificationService.notifyMentorSession(booking.getStudentId(), title, message, scheduleId, booking.getBookingId());
        notificationService.notifyMentorSession(booking.getMentorId(), title, message, scheduleId, booking.getBookingId());
    }

    // Sắp tới: giờ học gần nhất trước; đã qua / admin: mới nhất trước. Bỏ qua sort client gửi lên
    private PageResponse<BookingResponse> findPage(Specification<Booking> specification, Pageable pageable,
                                                   boolean newestFirst) {
        Sort sort = newestFirst ? Sort.by(Sort.Direction.DESC, SORT_FIELD) : Sort.by(Sort.Direction.ASC, SORT_FIELD);
        Page<Booking> page = bookingRepository.findAll(specification,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort));

        Map<UUID, String> names = new HashMap<>();
        return PageResponse.<BookingResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(booking -> toResponse(booking, names)).toList())
                .build();
    }

    // Lấy họ tên qua UserService, nhớ tạm trong 1 request để không gọi lại cùng 1 người
    private String getFullName(UUID userId, Map<UUID, String> names) {
        return names.computeIfAbsent(userId, id -> userService.getUserById(id).getFullName());
    }

    private BookingResponse toResponse(Booking booking, Map<UUID, String> names) {
        // Booking chờ thanh toán: kèm mã đơn SePay đang chờ để FE mở lại QR
        String paymentOrderCode = booking.getStatus() != BookingStatus.pending ? null
                : paymentOrderRepository.findFirstByBookingIdAndStatus(booking.getBookingId(), PaymentOrderStatus.PENDING)
                        .map(order -> order.getOrderCode()).orElse(null);
        return toResponse(booking, names, paymentOrderCode);
    }

    private BookingResponse toResponse(Booking booking, Map<UUID, String> names, String paymentOrderCode) {
        MentorSchedule schedule = booking.getSchedule();
        return BookingResponse.builder()
                .bookingId(booking.getBookingId())
                .scheduleId(schedule.getScheduleId())
                .mentorId(booking.getMentorId())
                .mentorName(getFullName(booking.getMentorId(), names))
                .studentId(booking.getStudentId())
                .studentName(getFullName(booking.getStudentId(), names))
                .startTime(UtcTimeUtil.toInstant(schedule.getStartTime()))
                .endTime(UtcTimeUtil.toInstant(schedule.getEndTime()))
                .price(schedule.getPrice())
                .meetingUrl(schedule.getMeetingUrl())
                .status(booking.getStatus() != null ? booking.getStatus().name() : null)
                .cancelReason(booking.getCancelReason())
                .cancelledAt(UtcTimeUtil.toInstant(booking.getCancelledAt()))
                .rescheduledFromId(booking.getRescheduledFromId())
                .createdAt(UtcTimeUtil.fromServerTime(booking.getCreatedAt()))
                .paymentOrderCode(paymentOrderCode)
                .build();
    }
}
