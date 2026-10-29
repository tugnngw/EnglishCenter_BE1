package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.CourseStatus;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.common.enums.PaymentOrderType;
import com.sba301.englishcenter.config.SePayProperties;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.entity.*;
import com.sba301.englishcenter.exception.*;
import com.sba301.englishcenter.repository.*;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.HashMap;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    // Lý do ghi vào booking khi đơn buổi kèm 1-1 không được thanh toán (Dev 5)
    static final String SESSION_EXPIRED_REASON = "Hết hạn thanh toán";
    static final String SESSION_CANCELLED_REASON = "Học viên hủy thanh toán";
    private final PaymentOrderRepository orders;
    private final PaymentTransactionRepository transactions;
    private final CourseRepository courses;
    private final UserRepository users;
    private final EnrollmentService enrollments;
    private final SePayProperties config;
    private final SePayWebhookVerifier verifier;
    private final JdbcTemplate jdbc;
    private final BookingPaymentService bookingPayments;
    private final ObjectMapper json = new ObjectMapper();

    @Override @Transactional
    public PaymentOrderResponse createOrder(UUID courseId) {
        checkConfiguration();
        UUID studentId = currentUserId();
        var student = users.findById(studentId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        String role = student.getRole().getRoleName();
        if (!"STUDENT".equalsIgnoreCase(role) && !"USER".equalsIgnoreCase(role)) throw new AppException(ErrorCode.UNAUTHORIZED);
        // Serializes repeated checkout clicks, including requests served by different app instances.
        jdbc.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 0))", studentId + ":" + courseId);
        if (enrollments.isEnrolled(studentId, courseId)) throw new AppException(ErrorCode.ALREADY_ENROLLED);
        var course = courses.findById(courseId).orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        BigDecimal price = course.getPrice();
        if (course.getStatus() != CourseStatus.ACTIVE || price == null || price.signum() <= 0
                || price.stripTrailingZeros().scale() > 0) throw new AppException(ErrorCode.COURSE_NOT_PURCHASABLE);
        expireDueOrders();
        var existing = orders.findByStudentIdAndCourseIdAndStatus(studentId, courseId, PaymentOrderStatus.PENDING);
        if (existing.isPresent()) return response(existing.get());
        Long sequence = jdbc.queryForObject("select nextval('payment_order_code_seq')", Long.class);
        Instant now = Instant.now();
        var order = PaymentOrder.builder().orderId(UUID.randomUUID()).orderCode(config.getPaymentPrefix() + sequence)
                .studentId(studentId).courseId(courseId).courseTitle(course.getTitle()).amount(price)
                .bankCode(config.getBankCode()).accountNumber(config.getAccountNumber()).accountName(config.getAccountName())
                .status(PaymentOrderStatus.PENDING).createdAt(now)
                .expiresAt(now.plusSeconds(config.getOrderExpiryMinutes() * 60L)).build();
        return response(orders.save(order));
    }

    @Override @Transactional
    public PaymentOrderResponse getOrder(String code) {
        var order = ownedOrder(code);
        expire(order);
        return response(order);
    }

    @Override @Transactional
    public PaymentOrderResponse cancelOrder(String code) {
        var order = ownedOrder(code);
        expire(order);
        if (order.getStatus() == PaymentOrderStatus.CANCELLED || order.getStatus() == PaymentOrderStatus.EXPIRED) return response(order);
        if (order.getStatus() != PaymentOrderStatus.PENDING) throw new AppException(ErrorCode.PAYMENT_ORDER_NOT_PENDING);
        order.setStatus(PaymentOrderStatus.CANCELLED);
        releaseBooking(order, SESSION_CANCELLED_REASON);
        return response(order);
    }

    // ===== Dev 5: đơn MENTOR_SESSION cho buổi kèm 1-1 =====

    @Override @Transactional
    public PaymentOrderResponse createMentorSessionOrder(UUID studentId, UUID bookingId, BigDecimal amount, String title) {
        checkConfiguration();
        // SePay chỉ nhận số tiền nguyên VND > 0
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
            throw new AppException(ErrorCode.SESSION_PRICE_INVALID);
        }
        Long sequence = jdbc.queryForObject("select nextval('payment_order_code_seq')", Long.class);
        Instant now = Instant.now();
        var order = PaymentOrder.builder().orderId(UUID.randomUUID()).orderCode(config.getPaymentPrefix() + sequence)
                .orderType(PaymentOrderType.MENTOR_SESSION).bookingId(bookingId)
                .studentId(studentId).courseTitle(title).amount(amount)
                .bankCode(config.getBankCode()).accountNumber(config.getAccountNumber()).accountName(config.getAccountName())
                .status(PaymentOrderStatus.PENDING).createdAt(now)
                .expiresAt(now.plusSeconds(config.getOrderExpiryMinutes() * 60L)).build();
        return response(orders.save(order));
    }

    @Override @Transactional
    public void cancelPendingBookingOrder(UUID bookingId) {
        orders.findFirstByBookingIdAndStatus(bookingId, PaymentOrderStatus.PENDING)
                .flatMap(found -> orders.lockByCode(found.getOrderCode()))
                .filter(order -> order.getStatus() == PaymentOrderStatus.PENDING)
                .ifPresent(order -> order.setStatus(PaymentOrderStatus.CANCELLED));
    }

    @Override @Transactional(readOnly = true)
    public PageResponse<PaymentTransaction> myPayments(Pageable pageable) {
        return page(transactions.findByStudentId(currentUserId(), pageable));
    }

    @Override @Transactional(readOnly = true)
    public PageResponse<PaymentOrderResponse> myOrders(Pageable pageable) {
        return page(orders.findByStudentId(currentUserId(), pageable).map(this::response));
    }

    @Override @Transactional(readOnly = true)
    public PageResponse<PaymentOrderResponse> adminOrders(PaymentOrderStatus status, Pageable pageable) {
        var orderPage = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        var studentNames = new HashMap<UUID, String>();
        if (!orderPage.isEmpty()) {
            var studentIds = orderPage.getContent().stream().map(PaymentOrder::getStudentId).distinct().toList();
            users.findAllById(studentIds).forEach(user -> studentNames.put(user.getUserId(), user.getFullName()));
        }
        return page(orderPage.map(order -> response(order, studentNames.get(order.getStudentId()))));
    }

    @Override @Transactional(readOnly = true)
    public PaymentOrderResponse adminOrder(UUID id) {
        var order = orders.findById(id).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        String studentName = users.findById(order.getStudentId()).map(User::getFullName).orElse(null);
        return response(order, studentName);
    }

    @Override @Transactional
    public void processWebhook(byte[] body, String timestamp, String signature) {
        verifier.verify(body, timestamp, signature);
        JsonNode event;
        try {
            event = json.readTree(body);
            if (event == null || !event.isObject() || !event.path("id").isIntegralNumber()
                    || !event.path("id").canConvertToLong() || event.path("id").asLong() <= 0
                    || !event.path("transferAmount").isNumber() || event.path("transferAmount").decimalValue().signum() <= 0
                    || !event.path("accountNumber").isTextual() || !event.path("transferType").isTextual()) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) { throw new AppException(ErrorCode.INVALID_WEBHOOK_DATA); }
        long eventId = event.get("id").asLong();
        String code = event.path("code").asText("");
        if (code.length() > 255) throw new AppException(ErrorCode.INVALID_WEBHOOK_DATA);
        int inserted = jdbc.update("insert into sepay_webhook_events(sepay_id,order_code,raw_payload) values (?,?,?) on conflict do nothing",
                eventId, code, new String(body, StandardCharsets.UTF_8));
        if (inserted == 0) return;
        if (!"in".equals(event.path("transferType").asText())) { outcome(eventId, "IGNORED_OUTGOING"); return; }
        var found = orders.lockByCode(code);
        if (found.isEmpty()) { outcome(eventId, "UNKNOWN_ORDER"); return; }
        var order = found.get();
        if (!order.getAccountNumber().equals(event.path("accountNumber").asText())
                || !event.path("subAccount").asText("").isBlank()) {
            outcome(eventId, "WRONG_ACCOUNT_OR_UNSUPPORTED_VA"); return;
        }
        expire(order);
        if (order.getStatus() == PaymentOrderStatus.PAID) { outcome(eventId, "EXTRA_TRANSFER_REVIEW_REQUIRED"); return; }
        if (order.getStatus() != PaymentOrderStatus.PENDING
                || order.getAmount().compareTo(event.get("transferAmount").decimalValue()) != 0) {
            order.setStatus(PaymentOrderStatus.REVIEW_REQUIRED);
            outcome(eventId, "AMOUNT_OR_ORDER_STATE_REVIEW_REQUIRED"); return;
        }
        if (order.getOrderType() == PaymentOrderType.MENTOR_SESSION) { settleMentorSession(order, event, eventId); return; }
        // Record and grant entitlement atomically. A retry rolls everything back on any DB failure.
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var transaction = PaymentTransaction.builder().transactionId(UUID.randomUUID())
                .orderId(order.getOrderId()).studentId(order.getStudentId()).courseId(order.getCourseId())
                .amount(order.getAmount()).paymentMethod("SEPAY").transactionType(PaymentTransaction.Type.course_purchase)
                .transactionStatus(PaymentTransaction.Status.pending).sepayTransactionId(eventId)
                .providerReference(event.path("referenceCode").asText("")).createdAt(now).lastUpdated(now).build();
        transactions.saveAndFlush(transaction);
        transaction.setTransactionStatus(PaymentTransaction.Status.success);
        transaction.setPaymentDate(now);
        transactions.saveAndFlush(transaction);
        // Registration creates USER accounts; their first settled purchase creates the student profile.
        jdbc.update("""
                update users u set role_id=r.role_id, last_updated=current_timestamp from roles r
                where u.user_id=? and r.role_name='STUDENT'
                and u.role_id=(select role_id from roles where role_name='USER')
                """, order.getStudentId());
        jdbc.update("""
                insert into student_profiles(user_id)
                select u.user_id from users u join roles r on r.role_id=u.role_id
                where u.user_id=? and r.role_name='STUDENT' on conflict(user_id) do nothing
                """, order.getStudentId());
        jdbc.update("""
                insert into enrollments(enrollment_id,student_id,course_id,payment_transaction_id,enrolled_date,status)
                values (?,?,?,?,?,'active') on conflict (student_id,course_id) where status = 'active' do nothing
                """, UUID.randomUUID(), order.getStudentId(), order.getCourseId(), transaction.getTransactionId(), now);
        order.setStatus(PaymentOrderStatus.PAID);
        order.setPaidAt(Instant.now());
        outcome(eventId, "PAID");
    }

    // Dev 5: booking chuyển confirmed + ghi giao dịch mentor_session trong cùng transaction với webhook
    private void settleMentorSession(PaymentOrder order, JsonNode event, long eventId) {
        UUID scheduleId = bookingPayments.confirmPaidBooking(order.getBookingId());
        if (scheduleId == null) {
            // Booking đã bị hủy trước khi tiền về: giữ tiền chờ đối soát, không xác nhận lịch
            order.setStatus(PaymentOrderStatus.REVIEW_REQUIRED);
            outcome(eventId, "BOOKING_NOT_PENDING_REVIEW_REQUIRED"); return;
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        var transaction = PaymentTransaction.builder().transactionId(UUID.randomUUID())
                .orderId(order.getOrderId()).studentId(order.getStudentId()).mentorScheduleId(scheduleId)
                .amount(order.getAmount()).paymentMethod("SEPAY").transactionType(PaymentTransaction.Type.mentor_session)
                .transactionStatus(PaymentTransaction.Status.pending).sepayTransactionId(eventId)
                .providerReference(event.path("referenceCode").asText("")).createdAt(now).lastUpdated(now).build();
        transactions.saveAndFlush(transaction);
        transaction.setTransactionStatus(PaymentTransaction.Status.success);
        transaction.setPaymentDate(now);
        transactions.saveAndFlush(transaction);
        order.setStatus(PaymentOrderStatus.PAID);
        order.setPaidAt(Instant.now());
        outcome(eventId, "PAID");
    }

    @Scheduled(fixedDelay = 60000) @Transactional
    public void expireOrders() { expireDueOrders(); }

    // Hết hạn đơn quá hạn; đơn buổi kèm thì hủy booking pending và mở lại slot (Dev 5)
    private void expireDueOrders() {
        Instant now = Instant.now();
        for (PaymentOrder order : orders.lockExpiredSessionOrders(now)) {
            order.setStatus(PaymentOrderStatus.EXPIRED);
            releaseBooking(order, SESSION_EXPIRED_REASON);
        }
        orders.expirePending(now);
    }

    private void releaseBooking(PaymentOrder order, String reason) {
        if (order.getOrderType() == PaymentOrderType.MENTOR_SESSION) {
            bookingPayments.releaseUnpaidBooking(order.getBookingId(), reason);
        }
    }

    private void outcome(long id, String outcome) {
        jdbc.update("update sepay_webhook_events set outcome = ? where sepay_id = ?", outcome, id);
    }

    private PaymentOrder ownedOrder(String code) {
        var order = orders.lockByCode(code).orElseThrow(() -> new AppException(ErrorCode.PAYMENT_ORDER_NOT_FOUND));
        if (!order.getStudentId().equals(currentUserId())) throw new AppException(ErrorCode.PAYMENT_ORDER_NOT_FOUND);
        return order;
    }

    private void expire(PaymentOrder order) {
        if (order.getStatus() == PaymentOrderStatus.PENDING && !order.getExpiresAt().isAfter(Instant.now())) {
            order.setStatus(PaymentOrderStatus.EXPIRED);
            releaseBooking(order, SESSION_EXPIRED_REASON);
        }
    }

    private void checkConfiguration() {
        if (config.getWebhookSecret().isBlank() || config.getBankCode().isBlank() || config.getAccountNumber().isBlank()
                || config.getAccountName().isBlank() || !config.getPaymentPrefix().matches("[A-Z]{1,5}")
                || config.getOrderExpiryMinutes() < 1 || config.getOrderExpiryMinutes() > 1440) {
            throw new AppException(ErrorCode.SEPAY_NOT_CONFIGURED);
        }
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }

    private PaymentOrderResponse response(PaymentOrder order) {
        return response(order, null);
    }

    private PaymentOrderResponse response(PaymentOrder order, String studentName) {
        PaymentOrderStatus status = order.getStatus();
        if (status == PaymentOrderStatus.PENDING && !order.getExpiresAt().isAfter(Instant.now())) status = PaymentOrderStatus.EXPIRED;
        String qr = status == PaymentOrderStatus.PENDING ? "https://vietqr.app/img?acc=" + encode(order.getAccountNumber())
                + "&bank=" + encode(order.getBankCode()) + "&amount=" + order.getAmount().toPlainString()
                + "&des=" + encode(order.getOrderCode()) : null;
        return new PaymentOrderResponse(order.getOrderId(), order.getOrderCode(), order.getStudentId(), order.getCourseId(), order.getCourseTitle(),
                order.getAmount(), "VND", status, order.getCreatedAt(), order.getExpiresAt(), order.getPaidAt(),
                order.getBankCode(), order.getAccountNumber(), order.getAccountName(), order.getOrderCode(), qr,
                order.getOrderType(), order.getBookingId(), studentName);
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private <T> PageResponse<T> page(Page<T> page) {
        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalPages(), page.getTotalElements(), page.getContent());
    }
}
