package com.sba301.englishcenter;

import com.sba301.englishcenter.dto.request.BookScheduleRequest;
import com.sba301.englishcenter.dto.response.BookingResponse;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.BookingService;
import com.sba301.englishcenter.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Dev 5 x Dev 4: đặt lịch 1-1 -> đơn SePay MENTOR_SESSION -> webhook -> booking confirmed.
 * Chỉ chạy với DB PostgreSQL dùng một lần (giống PaymentIntegrationTest), không bao giờ trỏ vào DB của app.
 */
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${PAYMENT_TEST_DB_URL}", "spring.datasource.username=postgres",
        "spring.datasource.password=dev4-test", "spring.docker.compose.enabled=false",
        "spring.jpa.show-sql=false", "logging.level.org.flywaydb=INFO",
        "jwt.signer-key=0123456789012345678901234567890123456789012345678901234567890123",
        "sepay.webhook-secret=" + SePayWebhookVerifierTest.SECRET,
        "sepay.bank-code=MBBank", "sepay.account-number=0000000001", "sepay.account-name=TEST ACCOUNT",
        "app.booking.reminder-job.enabled=false"
})
class MentorSessionPaymentIntegrationTest {
    @Autowired PaymentServiceImpl payments;
    @Autowired BookingService bookings;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    UUID studentId, mentorId, scheduleId;

    @BeforeEach void fixture() {
        studentId = UUID.randomUUID(); mentorId = UUID.randomUUID(); scheduleId = UUID.randomUUID();
        jdbc.update("insert into users(user_id,email,password,full_name,role_id) values (?,?,?,'Session student',(select role_id from roles where role_name='STUDENT'))",
                studentId, studentId + "@test.invalid", "not-a-login-password");
        // Trigger V1 chỉ cho admin tạo tài khoản MENTOR -> đặt app.current_user_id = admin trong cùng câu lệnh
        UUID admin = jdbc.queryForObject("select u.user_id from users u join roles r using(role_id) where r.role_name='ADMIN' limit 1", UUID.class);
        jdbc.update("""
                with actor as (select set_config('app.current_user_id', ?, true))
                insert into users(user_id,email,password,full_name,role_id)
                select ?, ?, 'not-a-login-password', 'Session mentor', (select role_id from roles where role_name='MENTOR') from actor
                """, admin.toString(), mentorId, mentorId + "@test.invalid");
        jdbc.update("insert into mentor_profiles(user_id,hourly_rate) values (?,200000)", mentorId);
        jdbc.update("""
                insert into mentor_schedules(schedule_id,mentor_id,start_time,end_time,status,price)
                values (?,?,(now() at time zone 'utc') + interval '48 hours',(now() at time zone 'utc') + interval '49 hours','available',200000)
                """, scheduleId, mentorId);
        var principal = UserPrincipal.create(users.findById(studentId).orElseThrow());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    BookingResponse book() {
        var request = new BookScheduleRequest();
        request.setScheduleId(scheduleId);
        return bookings.book(studentId, request);
    }
    byte[] payload(String orderCode, long id, int amount) {
        return ("{\"id\":" + id + ",\"gateway\":\"MBBank\",\"accountNumber\":\"0000000001\",\"transferType\":\"in\",\"transferAmount\":"
                + amount + ",\"code\":\"" + orderCode + "\",\"referenceCode\":\"SESSION" + id + "\"}").getBytes(StandardCharsets.UTF_8);
    }
    void deliver(byte[] body) throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        payments.processWebhook(body, timestamp, SePayWebhookVerifierTest.signature(body, timestamp));
    }
    long nextEvent() { return jdbc.queryForObject("select nextval('payment_order_code_seq')", Long.class); }
    String bookingStatus(UUID bookingId) { return jdbc.queryForObject("select status::text from bookings where booking_id=?", String.class, bookingId); }
    String slotStatus() { return jdbc.queryForObject("select status::text from mentor_schedules where schedule_id=?", String.class, scheduleId); }
    String orderStatus(String code) { return jdbc.queryForObject("select status from payment_orders where order_code=?", String.class, code); }
    int notifications(UUID userId) { return jdbc.queryForObject("select count(*) from notifications where user_id=?", Integer.class, userId); }

    @Test void bookingCreatesPendingSessionOrderWithServerPriceAndHoldsSlot() {
        var booking = book();
        assertEquals("pending", booking.getStatus());
        assertNotNull(booking.getPaymentOrderCode());
        var order = payments.getOrder(booking.getPaymentOrderCode());
        assertEquals("MENTOR_SESSION", order.orderType().name());
        assertEquals(booking.getBookingId(), order.bookingId());
        assertNull(order.courseId());
        assertEquals(200000, order.amount().intValueExact());
        assertTrue(order.qrUrl().contains("amount=200000"));
        assertEquals("booked", slotStatus());
        assertEquals(booking.getPaymentOrderCode(), bookings.getBooking(studentId, false, booking.getBookingId()).getPaymentOrderCode());
    }

    @Test void paidWebhookConfirmsBookingOnceAndRecordsMentorSessionTransaction() throws Exception {
        var booking = book();
        byte[] event = payload(booking.getPaymentOrderCode(), nextEvent(), 200000);
        deliver(event); deliver(event);
        assertEquals("confirmed", bookingStatus(booking.getBookingId()));
        assertEquals("PAID", orderStatus(booking.getPaymentOrderCode()));
        assertEquals("booked", slotStatus());
        assertEquals(1, jdbc.queryForObject("select count(*) from payment_transactions where student_id=? and transaction_type='mentor_session' and mentor_schedule_id=? and transaction_status='success'",
                Integer.class, studentId, scheduleId));
        assertEquals(0, jdbc.queryForObject("select count(*) from enrollments where student_id=?", Integer.class, studentId));
        assertEquals(1, notifications(studentId));
        assertEquals(1, notifications(mentorId));
        assertNull(bookings.getBooking(studentId, false, booking.getBookingId()).getPaymentOrderCode());
    }

    @Test void simultaneousRetriesConfirmOnce() throws Exception {
        var booking = book();
        byte[] event = payload(booking.getPaymentOrderCode(), nextEvent(), 200000);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var start = new CountDownLatch(1);
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 4; i++) tasks.add(executor.submit(() -> { start.await(); deliver(event); return null; }));
            start.countDown(); for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertEquals("confirmed", bookingStatus(booking.getBookingId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from payment_transactions where student_id=?", Integer.class, studentId));
        assertEquals(1, notifications(mentorId));
    }

    @Test void wrongAmountIsHeldForReviewAndBookingStaysUnpaid() throws Exception {
        var booking = book();
        deliver(payload(booking.getPaymentOrderCode(), nextEvent(), 100000));
        assertEquals("REVIEW_REQUIRED", orderStatus(booking.getPaymentOrderCode()));
        assertEquals("pending", bookingStatus(booking.getBookingId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from payment_transactions where student_id=?", Integer.class, studentId));
    }

    @Test void expiredOrderReleasesSlotAndLatePaymentNeedsReview() throws Exception {
        var booking = book();
        jdbc.update("update payment_orders set expires_at = now() - interval '1 minute' where order_code=?", booking.getPaymentOrderCode());
        payments.expireOrders();
        assertEquals("EXPIRED", orderStatus(booking.getPaymentOrderCode()));
        assertEquals("cancelled", bookingStatus(booking.getBookingId()));
        assertEquals("available", slotStatus());
        assertEquals("Hết hạn thanh toán", jdbc.queryForObject("select cancel_reason from bookings where booking_id=?", String.class, booking.getBookingId()));

        deliver(payload(booking.getPaymentOrderCode(), nextEvent(), 200000));
        assertEquals("REVIEW_REQUIRED", orderStatus(booking.getPaymentOrderCode()));
        assertEquals("cancelled", bookingStatus(booking.getBookingId()));
        // Slot đã mở lại nên học viên khác vẫn đặt được
        assertEquals("pending", book().getStatus());
    }

    @Test void cancellingPaymentOrderReleasesBooking() {
        var booking = book();
        payments.cancelOrder(booking.getPaymentOrderCode());
        assertEquals("CANCELLED", orderStatus(booking.getPaymentOrderCode()));
        assertEquals("cancelled", bookingStatus(booking.getBookingId()));
        assertEquals("available", slotStatus());
    }

    @Test void paidSessionStartingSoonIsRemindedExactlyOnce() throws Exception {
        // Slot bắt đầu sau 20 phút (trong khoảng nhắc 30 phút)
        jdbc.update("update mentor_schedules set start_time=(now() at time zone 'utc') + interval '20 minutes', end_time=(now() at time zone 'utc') + interval '80 minutes' where schedule_id=?", scheduleId);
        var booking = book();
        deliver(payload(booking.getPaymentOrderCode(), nextEvent(), 200000));
        assertEquals("confirmed", bookingStatus(booking.getBookingId()));
        int before = notifications(studentId);

        assertTrue(bookings.sendUpcomingReminders() >= 1);
        bookings.sendUpcomingReminders();

        assertNotNull(jdbc.queryForObject("select reminder_sent_at from bookings where booking_id=?", java.sql.Timestamp.class, booking.getBookingId()));
        assertEquals(before + 1, notifications(studentId));
        assertEquals(1, jdbc.queryForObject("select count(*) from notifications where user_id=? and title='Sắp đến giờ học'", Integer.class, mentorId));
    }

    @Test void unpaidSessionIsNotReminded() {
        jdbc.update("update mentor_schedules set start_time=(now() at time zone 'utc') + interval '20 minutes', end_time=(now() at time zone 'utc') + interval '80 minutes' where schedule_id=?", scheduleId);
        var booking = book();
        bookings.sendUpcomingReminders();
        assertNull(jdbc.queryForObject("select reminder_sent_at from bookings where booking_id=?", java.sql.Timestamp.class, booking.getBookingId()));
        assertEquals(0, notifications(studentId));
    }

    @Test void cancellingPendingBookingCancelsPaymentOrder() {
        var booking = book();
        bookings.cancel(studentId, booking.getBookingId(), null);
        assertEquals("CANCELLED", orderStatus(booking.getPaymentOrderCode()));
        assertEquals("cancelled", bookingStatus(booking.getBookingId()));
        assertEquals("available", slotStatus());
    }
}
