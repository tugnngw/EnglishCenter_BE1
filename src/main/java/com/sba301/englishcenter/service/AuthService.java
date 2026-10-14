package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.ChangePasswordRequest;
import com.sba301.englishcenter.dto.request.LoginRequest;
import com.sba301.englishcenter.dto.request.LogoutRequest;
import com.sba301.englishcenter.dto.request.RefreshTokenRequest;
import com.sba301.englishcenter.dto.request.RegisterRequest;
import com.sba301.englishcenter.dto.response.AuthResponse;
import com.sba301.englishcenter.dto.response.UserResponse;

import java.util.UUID;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(RefreshTokenRequest request);

    void logout(LogoutRequest request, String authHeader);

    /**
     * Change user password.
     * Validates current password, ensures new password is different.
     * Clears mustChangePassword flag and revokes all refresh tokens.
     *
     * @param userId Current user's ID
     * @param request Password change request
     */
    void changePassword(UUID userId, ChangePasswordRequest request);
}
