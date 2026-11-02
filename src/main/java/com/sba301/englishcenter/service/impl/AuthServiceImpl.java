package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.AuthTokenType;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.config.AppProperties;
import com.sba301.englishcenter.dto.request.ActivateAccountRequest;
import com.sba301.englishcenter.dto.request.ChangePasswordRequest;
import com.sba301.englishcenter.dto.request.LoginRequest;
import com.sba301.englishcenter.dto.request.LogoutRequest;
import com.sba301.englishcenter.dto.request.RefreshTokenRequest;
import com.sba301.englishcenter.dto.request.RegisterRequest;
import com.sba301.englishcenter.dto.response.ActivationInfoResponse;
import com.sba301.englishcenter.dto.response.AuthResponse;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.entity.AuthToken;
import com.sba301.englishcenter.entity.Role;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.AuthTokenRepository;
import com.sba301.englishcenter.repository.RoleRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.security.jwt.JwtTokenProvider;
import com.sba301.englishcenter.service.AuthService;
import com.sba301.englishcenter.service.EmailService;
import com.sba301.englishcenter.service.NotificationService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        Optional<User> existing = userRepository.findByEmail(request.getEmail());
        if (existing.isPresent()) {
            User user = existing.get();
            if (Boolean.TRUE.equals(user.getIsActive())) {
                throw new AppException(ErrorCode.USER_EXISTED);
            }
            // Email đã đăng ký nhưng chưa kích hoạt → cập nhật mật khẩu (người dùng
            // đăng ký lại với mật khẩu mới), phát hành link mới (link cũ mất hiệu lực)
            // và gửi lại email.
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setFullName(request.getFullName());
            if (StringUtils.hasText(request.getPhone())) {
                user.setPhone(request.getPhone());
            }
            userRepository.save(user);
            String rawToken = issueActivationToken(user);
            sendActivationEmail(user, rawToken);
            return toUserResponse(user);
        }

        Role defaultRole = roleRepository.findByRoleName(RoleName.USER.name())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(defaultRole)
                .isActive(false)
                .failedLoginAttempts(0)
                .build();

        User savedUser = userRepository.save(user);

        String rawToken = issueActivationToken(savedUser);
        sendActivationEmail(savedUser, rawToken);

        // StudentProfile không tạo ở đây; chỉ tạo khi user mua khóa và nâng cấp lên STUDENT.
        return toUserResponse(savedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivationInfoResponse verifyActivationToken(String token) {
        AuthToken authToken = findValidActivationToken(token);
        User user = authToken.getUser();
        return ActivationInfoResponse.builder()
                .email(user.getEmail())
                .fullName(user.getFullName())
                .build();
    }

    @Override
    @Transactional
    public AuthResponse activateAccount(ActivateAccountRequest request) {
        AuthToken authToken = findValidActivationToken(request.getToken());
        User user = authToken.getUser();

        if (Boolean.TRUE.equals(user.getIsActive())) {
            // Tài khoản đã kích hoạt rồi → link này không dùng lại được
            authToken.setRevoked(true);
            authTokenRepository.save(authToken);
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID);
        }

        // Mật khẩu đã được đặt ở bước đăng ký — chỉ cần xác minh token và kích hoạt.
        user.setIsActive(true);
        user.setLastUpdated(LocalDateTime.now());
        userRepository.save(user);

        authToken.setRevoked(true);
        authTokenRepository.save(authToken);

        try {
            notificationService.notify(user.getUserId(), NotificationType.general,
                    "Làm test trình độ miễn phí",
                    "Chào mừng bạn! Hãy làm bài test trình độ miễn phí để xác định lộ trình học phù hợp.");
        } catch (Exception e) {
            log.warn("Failed to send welcome notification to user {}: {}", user.getUserId(), e.getMessage());
        }

        log.info("Account activated via email link for user: {}", user.getEmail());
        return createSession(user);
    }

    @Override
    @Transactional
    public void resendActivation(String email) {
        Optional<User> maybeUser = userRepository.findByEmail(email);
        if (maybeUser.isEmpty() || Boolean.TRUE.equals(maybeUser.get().getIsActive())) {
            // Luôn trả OK generic (controller) — không tiết lộ email có tồn tại/chưa kích hoạt hay không
            return;
        }
        User user = maybeUser.get();
        String rawToken = issueActivationToken(user);
        sendActivationEmail(user, rawToken);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
            }
            userRepository.save(user);
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        return createSession(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        if (!tokenProvider.validateToken(token)) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        // Verify this is a REFRESH token
        Claims claims = tokenProvider.getClaimsFromToken(token);
        String tokenType = claims.get("tokenType", String.class);
        if (!"REFRESH".equals(tokenType)) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        String tokenHash = JwtTokenProvider.hashToken(token);
        AuthToken storedToken = authTokenRepository.findByTokenHashAndTokenType(tokenHash, AuthTokenType.REFRESH)
                .orElseThrow(() -> new AppException(ErrorCode.TOKEN_INVALID));

        if (Boolean.TRUE.equals(storedToken.getRevoked())) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        // Verify token claims match stored user
        User user = storedToken.getUser();
        String jwtSubject = claims.getSubject();
        String jwtUserId = claims.get("userId", String.class);

        if (!user.getEmail().equals(jwtSubject) || !user.getUserId().toString().equals(jwtUserId)) {
            log.warn("Token claims mismatch for user {}", user.getEmail());
            throw new AppException(ErrorCode.TOKEN_INVALID);
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.ACCOUNT_INACTIVE);
        }

        // Token Rotation: revoke old refresh token
        storedToken.setRevoked(true);
        authTokenRepository.save(storedToken);

        return createSession(user);
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request, String authHeader) {
        String accessToken = null;
        UUID authenticatedUserId = null;

        // Extract and validate access token from header
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            accessToken = authHeader.substring(7);
            try {
                if (tokenProvider.validateToken(accessToken)) {
                    authenticatedUserId = tokenProvider.getUserIdFromToken(accessToken);
                }
            } catch (Exception ex) {
                log.warn("Failed to extract user from access token: {}", ex.getMessage());
            }
        }

        // 1. Revoke Refresh Token (only if it belongs to authenticated user)
        final UUID logoutUserId = authenticatedUserId;
        if (request != null && StringUtils.hasText(request.getRefreshToken()) && logoutUserId != null) {
            try {
                if (tokenProvider.validateToken(request.getRefreshToken())) {
                    String refreshHash = JwtTokenProvider.hashToken(request.getRefreshToken());
                    authTokenRepository.findByTokenHashAndTokenType(refreshHash, AuthTokenType.REFRESH)
                            .ifPresent(token -> {
                                // Verify ownership: token must belong to authenticated user
                                if (token.getUser() != null && token.getUser().getUserId().equals(logoutUserId)) {
                                    token.setRevoked(true);
                                    authTokenRepository.save(token);
                                } else {
                                    log.warn("Attempted to revoke refresh token not owned by authenticated user");
                                }
                            });
                }
            } catch (Exception ex) {
                log.warn("Failed to revoke refresh token: {}", ex.getMessage());
            }
        }

        // 2. Blacklist Access Token (only the one in the Authorization header)
        if (accessToken != null && authenticatedUserId != null) {
            try {
                String accessHash = JwtTokenProvider.hashToken(accessToken);
                Date expiryDate = tokenProvider.getExpirationFromToken(accessToken);
                LocalDateTime expiresAt = expiryDate.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();

                AuthToken blacklistedToken = AuthToken.builder()
                        .tokenHash(accessHash)
                        .tokenType(AuthTokenType.ACCESS_REVOKED)
                        .user(null) // must be null per DB constraint chk_auth_token_user
                        .expiresAt(expiresAt)
                        .revoked(true)
                        .build();

                authTokenRepository.save(blacklistedToken);
            } catch (Exception ex) {
                log.warn("Failed to blacklist access token: {}", ex.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        // Ensure new password is different from old
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setLastUpdated(LocalDateTime.now());
        userRepository.save(user);

        // Revoke all refresh tokens to force re-login on other devices
        authTokenRepository.deleteAllByUser_UserIdAndTokenType(userId, AuthTokenType.REFRESH);

        log.info("Password changed for user: {}", user.getEmail());
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().getRoleName())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Phát hành token kích hoạt mới cho user: revoke toàn bộ token kích hoạt
     * còn hiệu lực trước đó (link cũ mất tác dụng), lưu SHA-256 hash vào auth_tokens.
     *
     * @return token raw (hex 64 ký tự) — chỉ xuất hiện trong link email, không lưu DB
     */
    private String issueActivationToken(User user) {
        List<AuthToken> active = authTokenRepository
                .findByUser_UserIdAndTokenTypeAndRevokedFalse(user.getUserId(), AuthTokenType.ACCOUNT_ACTIVATION);
        for (AuthToken old : active) {
            old.setRevoked(true);
        }
        authTokenRepository.saveAll(active);

        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String rawToken = HexFormat.of().formatHex(bytes);

        AuthToken token = AuthToken.builder()
                .tokenHash(JwtTokenProvider.hashToken(rawToken))
                .tokenType(AuthTokenType.ACCOUNT_ACTIVATION)
                .user(user)
                .expiresAt(LocalDateTime.now()
                        .plusHours(appProperties.getAuth().getActivation().getTokenExpiryHours()))
                .revoked(false)
                .build();
        authTokenRepository.save(token);

        return rawToken;
    }

    /** Tìm token kích hoạt theo hash, kiểm tra còn hiệu lực; không hợp lệ → ACTIVATION_TOKEN_INVALID. */
    private AuthToken findValidActivationToken(String rawToken) {
        AuthToken authToken = authTokenRepository
                .findByTokenHashAndTokenType(JwtTokenProvider.hashToken(rawToken), AuthTokenType.ACCOUNT_ACTIVATION)
                .orElseThrow(() -> new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID));

        if (Boolean.TRUE.equals(authToken.getRevoked()) || authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.ACTIVATION_TOKEN_INVALID);
        }
        return authToken;
    }

    /** Gửi email kích hoạt; báo lỗi nếu provider từ chối để tránh báo thành công giả. */
    private void sendActivationEmail(User user, String rawToken) {
        String link = appProperties.getFrontend().getBaseUrl() + "/activate?token=" + rawToken;
        try {
            emailService.sendAccountActivationEmail(user.getEmail(), user.getFullName(), link);
        } catch (Exception e) {
            log.warn("Failed to send activation email to {}: {}", user.getEmail(), e.getMessage());
            throw new IllegalStateException("Không gửi được email kích hoạt. Hãy kiểm tra cấu hình dịch vụ email.", e);
        }
    }

    /** Tạo phiên JWT (access + refresh, lưu refresh hash) — dùng cho login/refresh/activate. */
    private AuthResponse createSession(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(principal);

        AuthToken authToken = AuthToken.builder()
                .tokenHash(JwtTokenProvider.hashToken(refreshToken))
                .tokenType(AuthTokenType.REFRESH)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(tokenProvider.getRefreshTokenExpiry() / 1000))
                .revoked(false)
                .build();
        authTokenRepository.save(authToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(tokenProvider.getAccessTokenExpiry() / 1000)
                .user(toUserResponse(user))
                .build();
    }
}
