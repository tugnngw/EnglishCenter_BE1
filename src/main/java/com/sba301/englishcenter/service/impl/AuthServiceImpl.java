package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.AuthTokenType;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.request.ChangePasswordRequest;
import com.sba301.englishcenter.dto.request.LoginRequest;
import com.sba301.englishcenter.dto.request.LogoutRequest;
import com.sba301.englishcenter.dto.request.RefreshTokenRequest;
import com.sba301.englishcenter.dto.request.RegisterRequest;
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
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EXISTED);
        }

        Role defaultRole = roleRepository.findByRoleName(RoleName.USER.name())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(defaultRole)
                .isActive(true)
                .failedLoginAttempts(0)
                .build();

        User savedUser = userRepository.save(user);

        // StudentProfile is not created yet; it will be created when user purchases a course and upgrades to STUDENT.

        return toUserResponse(savedUser);
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

        UserPrincipal principal = UserPrincipal.create(user);
        String accessToken = tokenProvider.generateAccessToken(principal);
        String refreshToken = tokenProvider.generateRefreshToken(principal);
        String refreshHash = JwtTokenProvider.hashToken(refreshToken);

        LocalDateTime refreshExpiry = LocalDateTime.now()
                .plusSeconds(tokenProvider.getRefreshTokenExpiry() / 1000);

        AuthToken authToken = AuthToken.builder()
                .tokenHash(refreshHash)
                .tokenType(AuthTokenType.REFRESH)
                .user(user)
                .expiresAt(refreshExpiry)
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

        UserPrincipal principal = UserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateAccessToken(principal);
        String newRefreshToken = tokenProvider.generateRefreshToken(principal);
        String newRefreshHash = JwtTokenProvider.hashToken(newRefreshToken);

        LocalDateTime newRefreshExpiry = LocalDateTime.now()
                .plusSeconds(tokenProvider.getRefreshTokenExpiry() / 1000);

        AuthToken newAuthToken = AuthToken.builder()
                .tokenHash(newRefreshHash)
                .tokenType(AuthTokenType.REFRESH)
                .user(user)
                .expiresAt(newRefreshExpiry)
                .revoked(false)
                .build();
        authTokenRepository.save(newAuthToken);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(tokenProvider.getAccessTokenExpiry() / 1000)
                .user(toUserResponse(user))
                .build();
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
}
