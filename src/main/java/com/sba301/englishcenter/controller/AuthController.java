package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.ActivateAccountRequest;
import com.sba301.englishcenter.dto.request.ActivationTokenRequest;
import com.sba301.englishcenter.dto.request.ChangePasswordRequest;
import com.sba301.englishcenter.dto.request.LoginRequest;
import com.sba301.englishcenter.dto.request.LogoutRequest;
import com.sba301.englishcenter.dto.request.RefreshTokenRequest;
import com.sba301.englishcenter.dto.request.RegisterRequest;
import com.sba301.englishcenter.dto.request.ResendActivationRequest;
import com.sba301.englishcenter.dto.response.ActivationInfoResponse;
import com.sba301.englishcenter.dto.response.AuthResponse;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user authentication and authorization")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account by email",
            description = "Creates an INACTIVE account and sends an email with an activation link. " +
                    "The user sets the password on the /set-password page (POST /api/v1/auth/activate). " +
                    "Re-registering with an unactivated email re-sends a fresh link.")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Vui lòng kiểm tra email để kích hoạt tài khoản", user));
    }

    @PostMapping("/verify-activation-token")
    @Operation(summary = "Verify an account activation token",
            description = "Public endpoint used by the /set-password page: checks the token from the email link " +
                    "and returns the account's email and full name for display.")
    public ApiResponse<ActivationInfoResponse> verifyActivationToken(
            @Valid @RequestBody ActivationTokenRequest request) {
        return ApiResponse.success(authService.verifyActivationToken(request.getToken()));
    }

    @PostMapping("/activate")
    @Operation(summary = "Complete activation by setting the password",
            description = "Consumes the activation token from the email link, sets the user's password, " +
                    "activates the account and returns a JWT session (auto-login on the web).")
    public ApiResponse<AuthResponse> activate(@Valid @RequestBody ActivateAccountRequest request) {
        return ApiResponse.success("Kích hoạt tài khoản thành công", authService.activateAccount(request));
    }

    @PostMapping("/resend-activation")
    @Operation(summary = "Resend the activation email",
            description = "Sends a new activation link for an unactivated account. " +
                    "Always returns OK — does not reveal whether the email exists.")
    public ApiResponse<Void> resendActivation(@Valid @RequestBody ResendActivationRequest request) {
        authService.resendActivation(request.getEmail());
        return ApiResponse.success("Nếu email chưa được kích hoạt, link mới đã được gửi", null);
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using a valid refresh token (Token Rotation)")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate tokens")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(request, authHeader);
        return ResponseEntity.ok(ApiResponse.success("Logout successful", null));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password",
            description = "Change user password. Required for first-login after admin creates mentor account. " +
                    "Validates current password and ensures new password is different. Revokes all refresh tokens.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        authService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully", null));
    }
}
