package com.sba301.englishcenter.websocket;

import com.sba301.englishcenter.repository.AuthTokenRepository;
import com.sba301.englishcenter.security.CustomUserDetailsService;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private AuthTokenRepository authTokenRepository;

    @InjectMocks
    private StompAuthChannelInterceptor interceptor;

    private final MessageChannel channel = mock(MessageChannel.class);
    private final UUID userId = UUID.randomUUID();

    private UserPrincipal principal() {
        return UserPrincipal.builder().userId(userId).email("u@test.com").password("x").roleName("STUDENT")
                .active(true).authorities(List.of()).build();
    }

    private Message<byte[]> frame(StompCommand command, String authorization, String destination,
                                  UsernamePasswordAuthenticationToken user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("CONNECT không có token -> từ chối")
    void connect_withoutToken() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CONNECT bằng refresh token / token sai -> từ chối")
    void connect_withNonAccessToken() {
        Claims claims = mock(Claims.class);
        when(tokenProvider.validateToken("refresh")).thenReturn(true);
        when(tokenProvider.getClaimsFromToken("refresh")).thenReturn(claims);
        when(claims.get("tokenType", String.class)).thenReturn("REFRESH");

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer refresh", null, null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("CONNECT bằng access token hợp lệ -> gắn user vào phiên STOMP")
    void connect_withAccessToken() {
        Claims claims = mock(Claims.class);
        when(tokenProvider.validateToken("good")).thenReturn(true);
        when(tokenProvider.getClaimsFromToken("good")).thenReturn(claims);
        when(claims.get("tokenType", String.class)).thenReturn("ACCESS");
        when(authTokenRepository.existsByTokenHashAndTokenType(any(), any())).thenReturn(false);
        when(tokenProvider.getUsernameFromToken("good")).thenReturn("u@test.com");
        when(userDetailsService.loadUserByUsername("u@test.com")).thenReturn(principal());

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), channel);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(((UserPrincipal) ((UsernamePasswordAuthenticationToken) accessor.getUser()).getPrincipal()).getUserId())
                .isEqualTo(userId);
    }

    @Test
    @DisplayName("SUBSCRIBE topic của chính mình -> cho phép; topic người khác -> từ chối")
    void subscribe_ownTopicOnly() {
        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(principal(), null, List.of());

        assertThat(interceptor.preSend(frame(StompCommand.SUBSCRIBE, null,
                "/topic/notifications/" + userId, user), channel)).isNotNull();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null,
                "/topic/notifications/" + UUID.randomUUID(), user), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null,
                "/topic/notifications/" + userId, null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("SEND từ client -> từ chối (thông báo chỉ do server đẩy)")
    void send_denied() {
        UsernamePasswordAuthenticationToken user = new UsernamePasswordAuthenticationToken(principal(), null, List.of());
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null, "/app/anything", user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }
}
