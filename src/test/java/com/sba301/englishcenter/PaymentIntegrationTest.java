package com.sba301.englishcenter;

import com.fasterxml.jackson.databind.*;
import com.sba301.englishcenter.common.enums.PaymentOrderStatus;
import com.sba301.englishcenter.dto.response.PaymentOrderResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.*;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import com.sba301.englishcenter.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Run only against a separate disposable PostgreSQL database, never the application database. */
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${PAYMENT_TEST_DB_URL}", "spring.datasource.username=postgres",
        "spring.datasource.password=dev4-test", "spring.docker.compose.enabled=false",
        "spring.jpa.show-sql=false", "logging.level.org.flywaydb=INFO",
        "jwt.signer-key=0123456789012345678901234567890123456789012345678901234567890123",
        "sepay.webhook-secret=" + SePayWebhookVerifierTest.SECRET,
        "sepay.bank-code=MBBank", "sepay.account-number=0000000001", "sepay.account-name=TEST ACCOUNT"
})
class PaymentIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired EnrollmentService enrollments;
    @Autowired CourseService courses;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @LocalServerPort int port;
    final ObjectMapper json = new ObjectMapper();
    final HttpClient http = HttpClient.newHttpClient();
    UUID studentId, courseId, lessonId;
    String token;

    @BeforeEach void fixture() {
        studentId = UUID.randomUUID(); courseId = UUID.randomUUID(); lessonId = UUID.randomUUID();
        jdbc.update("insert into users(user_id,email,password,full_name,role_id) values (?,?,?,'Payment test',(select role_id from roles where role_name='STUDENT'))",
                studentId, studentId + "@test.invalid", "not-a-login-password");
        UUID admin = jdbc.queryForObject("select u.user_id from users u join roles r using(role_id) where r.role_name='ADMIN' limit 1", UUID.class);
        jdbc.update("insert into courses(course_id,created_by_user_id,code,title,price,status) values (?,?,?,'Test course',10000,'active')",
                courseId, admin, courseId.toString());
        jdbc.update("insert into course_content(content_id,course_id,content_type,title,description,content_data) values (?,?,'lesson','Restricted','Private lesson',cast(? as jsonb))",
                lessonId, courseId, "{\"videoUrl\":\"https://private.invalid/video\",\"isPreview\":false}");
        authenticate();
    }

    void authenticate() {
        var principal = UserPrincipal.create(users.findById(studentId).orElseThrow());
        token = tokens.generateAccessToken(principal);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }
    PaymentOrderResponse order() { return payments.createOrder(courseId); }

    byte[] payload(PaymentOrderResponse order, long id, int amount, String account) {
        return ("{\"id\":" + id + ",\"gateway\":\"MBBank\",\"accountNumber\":\"" + account
                + "\",\"transferType\":\"in\",\"transferAmount\":" + amount + ",\"code\":\"" + order.orderCode()
                + "\",\"referenceCode\":\"TEST" + id + "\"}").getBytes(StandardCharsets.UTF_8);
    }
    void deliver(byte[] body) throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        payments.processWebhook(body, timestamp, SePayWebhookVerifierTest.signature(body, timestamp));
    }
    long nextEvent() { return jdbc.queryForObject("select nextval('payment_order_code_seq')", Long.class); }
    int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where student_id=? and course_id=?", Integer.class, studentId, courseId);
    }

    @Test void checkoutReusesPendingOrderAndUsesServerPrice() {
        var first = order(); var second = order();
        assertEquals(first.orderId(), second.orderId());
        assertEquals(10000, first.amount().intValueExact());
        assertTrue(first.orderCode().matches("EC[0-9]{10}"));
        assertTrue(first.qrUrl().contains("amount=10000"));
    }

    @Test void firstPurchaseUpgradesUserOnlyAfterSettlementAndExistingTokenSeesNewRole() throws Exception {
        jdbc.update("update users set role_id=(select role_id from roles where role_name='USER') where user_id=?", studentId);
        authenticate();
        String body = "{\"courseId\":\"" + courseId + "\"}";
        var created = request("POST", "/api/v1/payments/orders", body, true);
        assertEquals(200, created.statusCode(), created.body());
        var code = json.readTree(created.body()).path("result").path("orderCode").asText();
        assertEquals("USER", users.findById(studentId).orElseThrow().getRole().getRoleName());
        assertEquals(0, jdbc.queryForObject("select count(*) from student_profiles where user_id=?", Integer.class, studentId));
        byte[] event = payload(payments.getOrder(code), nextEvent(), 10000, "0000000001");
        deliver(event); deliver(event);
        assertEquals("STUDENT", users.findById(studentId).orElseThrow().getRole().getRoleName());
        assertEquals(1, jdbc.queryForObject("select count(*) from student_profiles where user_id=?", Integer.class, studentId));
        var profile = request("GET", "/api/v1/account/me", null, true);
        assertEquals(200, profile.statusCode(), profile.body());
        assertEquals("STUDENT", json.readTree(profile.body()).path("result").path("role").asText());
        assertEquals(1, count("enrollments"));
    }

    @Test void cancelledOrUnderpaidFirstPurchaseDoesNotPromoteUser() throws Exception {
        jdbc.update("update users set role_id=(select role_id from roles where role_name='USER') where user_id=?", studentId);
        authenticate();
        var order = order(); payments.cancelOrder(order.orderCode());
        deliver(payload(order, nextEvent(), 10000, "0000000001"));
        assertEquals("USER", users.findById(studentId).orElseThrow().getRole().getRoleName());
        assertEquals(0, jdbc.queryForObject("select count(*) from student_profiles where user_id=?", Integer.class, studentId));
        assertEquals(0, count("enrollments"));
    }

    @Test void successAndRepeatedWebhookGrantExactlyOneEnrollment() throws Exception {
        var order = order(); byte[] event = payload(order, nextEvent(), 10000, "0000000001");
        deliver(event); deliver(event);
        assertEquals(PaymentOrderStatus.PAID, payments.getOrder(order.orderCode()).status());
        assertTrue(enrollments.isEnrolled(studentId, courseId));
        assertEquals(1, count("payment_transactions")); assertEquals(1, count("enrollments"));
        assertNotNull(courses.getCourseContents(courseId).get(0).getContentData());
        assertThrows(AppException.class, this::order);
        assertEquals(1, jdbc.queryForObject("select count(*) from payment_transactions where student_id=? and confirmation_code is not null and confirmation_code_sent=false", Integer.class, studentId));
    }

    @Test void simultaneousWebhookRetriesDoNotDuplicateMoneyOrEntitlement() throws Exception {
        var order = order(); byte[] event = payload(order, nextEvent(), 10000, "0000000001");
        var executor = Executors.newFixedThreadPool(4);
        try {
            var start = new CountDownLatch(1);
            List<Future<?>> tasks = new ArrayList<>();
            for (int i=0; i<4; i++) tasks.add(executor.submit(() -> { start.await(); deliver(event); return null; }));
            start.countDown(); for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertEquals(1, count("enrollments")); assertEquals(1, count("payment_transactions"));
    }

    @Test void concurrentCheckoutClicksReuseOneOrder() throws Exception {
        var executor = Executors.newFixedThreadPool(3);
        try {
            List<Future<UUID>> tasks = new ArrayList<>();
            for (int i=0;i<3;i++) tasks.add(executor.submit(() -> {
                authenticate(); try { return order().orderId(); } finally { SecurityContextHolder.clearContext(); }
            }));
            Set<UUID> ids = new HashSet<>(); for (var task : tasks) ids.add(task.get(20, TimeUnit.SECONDS));
            assertEquals(1, ids.size());
        } finally { executor.shutdownNow(); }
    }

    @Test void wrongAmountIsHeldForReviewWithoutEnrollment() throws Exception {
        var order = order(); deliver(payload(order, nextEvent(), 9999, "0000000001"));
        assertEquals(PaymentOrderStatus.REVIEW_REQUIRED, payments.getOrder(order.orderCode()).status());
        assertEquals(0, count("enrollments")); assertEquals(0, count("payment_transactions"));
    }

    @Test void wrongAccountDoesNotChangeOrder() throws Exception {
        var order = order(); deliver(payload(order, nextEvent(), 10000, "OTHER"));
        assertEquals(PaymentOrderStatus.PENDING, payments.getOrder(order.orderCode()).status());
        assertFalse(enrollments.isEnrolled(studentId, courseId));
    }

    @Test void databaseFailureRollsBackReceiptAndMoneySoRetryCanSucceed() throws Exception {
        var order = order(); long id = nextEvent(); byte[] event = payload(order, id, 10000, "0000000001");
        jdbc.execute("alter table enrollments add constraint dev4_test_reject_enrollment check (student_id <> '" + studentId + "'::uuid)");
        try {
            assertThrows(RuntimeException.class, () -> deliver(event));
            assertEquals(0, count("payment_transactions"));
            assertEquals(0, count("enrollments"));
            assertEquals(0, jdbc.queryForObject("select count(*) from sepay_webhook_events where sepay_id=?", Integer.class, id));
            assertEquals(PaymentOrderStatus.PENDING, payments.getOrder(order.orderCode()).status());
        } finally {
            jdbc.execute("alter table enrollments drop constraint dev4_test_reject_enrollment");
        }
        deliver(event);
        assertTrue(enrollments.isEnrolled(studentId, courseId));
        assertEquals(1, count("payment_transactions"));
    }

    @Test void secondTransferForPaidOrderIsRecordedForReviewWithoutDoubleGrant() throws Exception {
        var order = order(); deliver(payload(order, nextEvent(), 10000, "0000000001"));
        long extra = nextEvent(); deliver(payload(order, extra, 10000, "0000000001"));
        assertEquals(PaymentOrderStatus.PAID, payments.getOrder(order.orderCode()).status());
        assertEquals(1, count("enrollments")); assertEquals(1, count("payment_transactions"));
        assertEquals("EXTRA_TRANSFER_REVIEW_REQUIRED", jdbc.queryForObject("select outcome from sepay_webhook_events where sepay_id=?", String.class, extra));
    }

    @Test void draftNullFreeOrFractionalPriceCannotBePurchased() {
        jdbc.update("update courses set status='draft' where course_id=?", courseId);
        assertThrows(AppException.class, this::order);
        jdbc.update("update courses set status='active', price=null where course_id=?", courseId);
        assertThrows(AppException.class, this::order);
        jdbc.update("update courses set price=0 where course_id=?", courseId);
        assertThrows(AppException.class, this::order);
        jdbc.update("update courses set price=10000.5 where course_id=?", courseId);
        assertThrows(AppException.class, this::order);
    }

    @Test void adminCanInspectOrdersAndReviewEventsAndSwaggerDocumentsNewApis() throws Exception {
        var order = order(); deliver(payload(order, nextEvent(), 9000, "0000000001"));
        UUID admin = jdbc.queryForObject("select u.user_id from users u join roles r using(role_id) where r.role_name='ADMIN' limit 1", UUID.class);
        token = tokens.generateAccessToken(UserPrincipal.create(users.findById(admin).orElseThrow()));
        for (String path : List.of("/api/v1/admin/payments?status=REVIEW_REQUIRED",
                "/api/v1/admin/payments/" + order.orderId(),
                "/api/v1/admin/payments/" + order.orderId() + "/webhook-events",
                "/api/v1/admin/payments/webhook-events")) {
            var result = request("GET", path, null, true);
            assertEquals(200, result.statusCode(), result.body());
            assertEquals(1000, json.readTree(result.body()).path("code").asInt());
        }
        var docs = request("GET", "/v3/api-docs", null, false);
        assertEquals(200, docs.statusCode(), docs.body());
        assertTrue(json.readTree(docs.body()).path("paths").has("/api/v1/payments/sepay/webhook"));
    }

    @Test void cancelledOrderReceivingMoneyIsHeldForReview() throws Exception {
        var order = order(); payments.cancelOrder(order.orderCode());
        deliver(payload(order, nextEvent(), 10000, "0000000001"));
        assertEquals(PaymentOrderStatus.REVIEW_REQUIRED, payments.getOrder(order.orderCode()).status());
        assertEquals(0, count("enrollments"));
    }

    @Test void expiredOrderDoesNotGrantAccess() throws Exception {
        var order = order();
        jdbc.update("update payment_orders set expires_at=current_timestamp - interval '1 minute' where order_id=?", order.orderId());
        assertEquals(PaymentOrderStatus.EXPIRED, payments.getOrder(order.orderCode()).status());
        deliver(payload(order, nextEvent(), 10000, "0000000001"));
        assertEquals(PaymentOrderStatus.REVIEW_REQUIRED, payments.getOrder(order.orderCode()).status());
        assertEquals(0, count("enrollments"));
    }

    @Test void unpaidContentCannotLeakThroughJson() {
        var content = courses.getCourseContents(courseId).get(0);
        assertTrue(content.getIsLocked()); assertNull(content.getVideoUrl());
        assertNull(content.getContentData()); assertNull(content.getDescription());
        jdbc.update("update course_content set content_data=cast(? as jsonb) where content_id=?",
                "{\"videoUrl\":\"https://preview.invalid\",\"isPreview\":true}", lessonId);
        assertFalse(courses.getCourseContents(courseId).get(0).getIsLocked());
    }

    @Test void otherStudentCannotReadOrCancelOrder() {
        var order = order(); studentId = UUID.randomUUID();
        var principal = UserPrincipal.builder().userId(studentId).roleName("STUDENT").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        assertThrows(AppException.class, () -> payments.getOrder(order.orderCode()));
        assertThrows(AppException.class, () -> payments.cancelOrder(order.orderCode()));
    }

    @Test void httpSecurityAndSignedWebhookWorkWithExistingEnvelope() throws Exception {
        assertEquals(401, request("GET", "/api/v1/me/enrollments", null, false).statusCode());
        assertEquals(403, request("GET", "/api/v1/admin/payments", null, true).statusCode());
        var created = request("POST", "/api/v1/payments/orders", "{\"courseId\":\"" + courseId + "\",\"amount\":1}", true);
        assertEquals(200, created.statusCode(), created.body());
        var root = json.readTree(created.body()); assertEquals(1000, root.get("code").asInt());
        String code = root.path("result").path("orderCode").asText();
        assertEquals(10000, root.path("result").path("amount").asInt());
        byte[] body = payload(payments.getOrder(code), nextEvent(), 10000, "0000000001");
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        var webhook = HttpRequest.newBuilder(URI.create(base() + "/api/v1/payments/sepay/webhook"))
                .header("Content-Type", "application/json").header("X-SePay-Timestamp", timestamp)
                .header("X-SePay-Signature", SePayWebhookVerifierTest.signature(body, timestamp))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        var result = http.send(webhook, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, result.statusCode(), result.body()); assertTrue(json.readTree(result.body()).get("success").asBoolean());
        assertEquals(401, request("POST", "/api/v1/payments/sepay/webhook", "{}", false).statusCode());
        assertEquals(200, request("GET", "/api/v1/me/payments", null, true).statusCode());
        assertEquals(200, request("GET", "/api/v1/me/enrollments", null, true).statusCode());
    }

    String base() { return "http://localhost:" + port; }
    HttpResponse<String> request(String method, String path, String body, boolean auth) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/json");
        if (auth) request.header("Authorization", "Bearer " + token);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
