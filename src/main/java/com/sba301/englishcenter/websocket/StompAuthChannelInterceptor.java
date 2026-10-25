package com.sba301.englishcenter.websocket;

import com.sba301.englishcenter.common.enums.AuthTokenType;
import com.sba301.englishcenter.config.WebSocketConfig;
import com.sba301.englishcenter.repository.AuthTokenRepository;
import com.sba301.englishcenter.security.CustomUserDetailsService;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;

/**
 * Xác thực và phân quyền cho kết nối STOMP (TASK-D5-02).
 * - CONNECT: bắt buộc header "Authorization: Bearer <access token>", kiểm tra giống JwtAuthenticationFilter
 *   (chữ ký, loại ACCESS, chưa bị thu hồi, tài khoản còn hoạt động).
 * - SUBSCRIBE: chỉ được nghe đúng /topic/notifications/{userId của mình} (ownership).
 * - SEND: không mở đích gửi nào cho client (thông báo chỉ do server đẩy).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AuthTokenRepository authTokenRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> checkSubscribe(accessor);
            case SEND -> throw new AccessDeniedException("Client không được gửi message lên server");
            default -> { }
        }
        return message;
    }

    private Principal authenticate(String header) {
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("Thiếu access token");
        }
        String jwt = header.substring(BEARER_PREFIX.length());
        if (!tokenProvider.validateToken(jwt)
                || !"ACCESS".equals(tokenProvider.getClaimsFromToken(jwt).get("tokenType", String.class))
                || authTokenRepository.existsByTokenHashAndTokenType(JwtTokenProvider.hashToken(jwt), AuthTokenType.ACCESS_REVOKED)) {
            throw new AccessDeniedException("Access token không hợp lệ");
        }
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserByUsername(tokenProvider.getUsernameFromToken(jwt));
        if (!principal.isEnabled() || !principal.isAccountNonLocked()) {
            throw new AccessDeniedException("Tài khoản không hoạt động");
        }
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    private void checkSubscribe(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth)
                || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AccessDeniedException("Chưa xác thực");
        }
        String allowed = WebSocketConfig.NOTIFICATION_TOPIC_PREFIX + principal.getUserId();
        if (!allowed.equals(accessor.getDestination())) {
            log.warn("User {} tried to subscribe {}", principal.getUserId(), accessor.getDestination());
            throw new AccessDeniedException("Chỉ được nhận thông báo của chính mình");
        }
    }
}
