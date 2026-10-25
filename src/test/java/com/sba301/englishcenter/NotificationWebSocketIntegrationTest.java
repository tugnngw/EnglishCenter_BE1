package com.sba301.englishcenter;

import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import com.sba301.englishcenter.service.NotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-D5-02: thông báo realtime qua WebSocket STOMP (/ws-connect, /topic/notifications/{userId}).
 * Chỉ chạy với DB PostgreSQL dùng một lần (giống PaymentIntegrationTest).
 */
@EnabledIfEnvironmentVariable(named = "PAYMENT_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${PAYMENT_TEST_DB_URL}", "spring.datasource.username=postgres",
        "spring.datasource.password=dev4-test", "spring.docker.compose.enabled=false",
        "spring.jpa.show-sql=false", "logging.level.org.flywaydb=INFO",
        "jwt.signer-key=0123456789012345678901234567890123456789012345678901234567890123",
        "app.booking.reminder-job.enabled=false"
})
class NotificationWebSocketIntegrationTest {
    @Autowired NotificationService notifications;
    @Autowired JwtTokenProvider tokens;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @LocalServerPort int port;
    WebSocketStompClient client;
    UUID userId, otherId;

    @BeforeEach void fixture() {
        userId = createStudent(); otherId = createStudent();
        client = new WebSocketStompClient(new StandardWebSocketClient());
    }
    @AfterEach void stop() { client.stop(); }

    UUID createStudent() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users(user_id,email,password,full_name,role_id) values (?,?,?,'WS student',(select role_id from roles where role_name='STUDENT'))",
                id, id + "@test.invalid", "not-a-login-password");
        return id;
    }
    String token(UUID id) { return tokens.generateAccessToken(UserPrincipal.create(users.findById(id).orElseThrow())); }

    /** Ghi lại message nhận được và frame ERROR / lỗi kết nối. */
    static class Recorder extends StompSessionHandlerAdapter {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
        @Override public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(String.valueOf(headers.getFirst("message")));
        }
        @Override public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable ex) { errors.add(ex.toString()); }
        @Override public void handleTransportError(StompSession session, Throwable ex) { errors.add(ex.toString()); }
        StompFrameHandler collector() {
            return new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
                @Override public void handleFrame(StompHeaders headers, Object payload) {
                    messages.add(new String((byte[]) payload, StandardCharsets.UTF_8));
                }
            };
        }
    }

    StompSession connect(String bearer, Recorder recorder) throws Exception {
        StompHeaders connectHeaders = new StompHeaders();
        if (bearer != null) connectHeaders.add("Authorization", "Bearer " + bearer);
        return client.connectAsync("ws://localhost:" + port + "/ws-connect", new WebSocketHttpHeaders(), connectHeaders, recorder)
                .get(5, TimeUnit.SECONDS);
    }

    void notifyInTransaction(UUID target, String title) {
        tx.executeWithoutResult(s -> notifications.notify(target, NotificationType.general, title, "Nội dung test"));
    }

    @Test void ownerReceivesNotificationRealtimeAfterCommit() throws Exception {
        Recorder recorder = new Recorder();
        StompSession session = connect(token(userId), recorder);
        session.subscribe("/topic/notifications/" + userId, recorder.collector());
        Thread.sleep(300); // chờ broker ghi nhận subscription

        notifyInTransaction(userId, "Xin chào realtime");

        String json = recorder.messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(json, "Không nhận được thông báo qua WebSocket");
        assertTrue(json.contains("Xin chào realtime"));
        assertTrue(json.contains("\"type\":\"general\""));
        assertEquals(1, jdbc.queryForObject("select count(*) from notifications where user_id=?", Integer.class, userId));
    }

    @Test void rolledBackNotificationIsNotPushed() throws Exception {
        Recorder recorder = new Recorder();
        StompSession session = connect(token(userId), recorder);
        session.subscribe("/topic/notifications/" + userId, recorder.collector());
        Thread.sleep(300);

        tx.executeWithoutResult(s -> {
            notifications.notify(userId, NotificationType.general, "Sẽ rollback", "x");
            s.setRollbackOnly();
        });

        assertNull(recorder.messages.poll(1500, TimeUnit.MILLISECONDS));
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where user_id=?", Integer.class, userId));
    }

    @Test void cannotSubscribeToAnotherUsersTopic() throws Exception {
        Recorder recorder = new Recorder();
        StompSession session = connect(token(userId), recorder);
        session.subscribe("/topic/notifications/" + otherId, recorder.collector());

        assertNotNull(recorder.errors.poll(5, TimeUnit.SECONDS), "Server phải trả ERROR khi nghe topic của người khác");
        notifyInTransaction(otherId, "Riêng tư");
        assertNull(recorder.messages.poll(1500, TimeUnit.MILLISECONDS));
    }

    @Test void connectWithoutTokenIsRejected() {
        Recorder recorder = new Recorder();
        assertThrows(Exception.class, () -> {
            StompSession session = connect(null, recorder);
            // Một số phiên bản trả CONNECTED trễ / đóng ngay: coi như bị từ chối nếu có ERROR
            String error = recorder.errors.poll(3, TimeUnit.SECONDS);
            if (error != null || !session.isConnected()) throw new IllegalStateException("rejected: " + error);
        });
    }
}
