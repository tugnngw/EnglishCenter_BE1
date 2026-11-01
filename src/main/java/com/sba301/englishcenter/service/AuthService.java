package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.ActivateAccountRequest;
import com.sba301.englishcenter.dto.request.ChangePasswordRequest;
import com.sba301.englishcenter.dto.request.LoginRequest;
import com.sba301.englishcenter.dto.request.LogoutRequest;
import com.sba301.englishcenter.dto.request.RefreshTokenRequest;
import com.sba301.englishcenter.dto.request.RegisterRequest;
import com.sba301.englishcenter.dto.response.ActivationInfoResponse;
import com.sba301.englishcenter.dto.response.AuthResponse;
import com.sba301.englishcenter.dto.response.UserResponse;

import java.util.UUID;

public interface AuthService {

    /**
     * Đăng ký bằng email: tạo tài khoản CHƯA kích hoạt (isActive=false,
     * chưa có mật khẩu thật) và gửi email chứa link đặt mật khẩu.
     * Email đã tồn tại nhưng chưa kích hoạt → gửi lại email mới (link cũ hết hiệu lực).
     */
    UserResponse register(RegisterRequest request);

    /** Kiểm tra token kích hoạt từ link email (trang /set-password). */
    ActivationInfoResponse verifyActivationToken(String token);

    /**
     * Hoàn tất kích hoạt: đặt mật khẩu từ link email, kích hoạt tài khoản
     * và trả AuthResponse (access + refresh) để FE tự đăng nhập luôn.
     */
    AuthResponse activateAccount(ActivateAccountRequest request);

    /** Gửi lại email kích hoạt cho tài khoản chưa active (luôn trả OK, không lộ email tồn tại hay không). */
    void resendActivation(String email);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(LogoutRequest request, String authHeader);

    /**
     * Change user password.
     * Validates current password, ensures new password is different.
     * Revokes all refresh tokens to force re-login on other devices.
     *
     * @param userId Current user's ID
     * @param request Password change request
     */
    void changePassword(UUID userId, ChangePasswordRequest request);
}
