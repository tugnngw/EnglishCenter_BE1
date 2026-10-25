package com.sba301.englishcenter.config;

import com.sba301.englishcenter.websocket.StompAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Hạ tầng WebSocket STOMP cho thông báo realtime (TASK-D5-02).
 * - Endpoint bắt tay: /ws-connect (WebSocket thuần, client gửi JWT trong header STOMP CONNECT).
 * - Server đẩy thông báo tới /topic/notifications/{userId}; chỉ chính user đó được SUBSCRIBE
 *   (kiểm tra trong {@link StompAuthChannelInterceptor}).
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws-connect";
    public static final String NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    // Giống CORS của REST: mặc định cho mọi origin vì xác thực bằng JWT trong frame CONNECT, không dùng cookie
    @Value("${app.websocket.allowed-origins:*}")
    private String[] allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT).setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }
}
