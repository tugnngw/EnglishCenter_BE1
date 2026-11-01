package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hoàn tất kích hoạt tài khoản: người dùng bấm link trong email
 * (POST /api/v1/auth/activate). Mật khẩu đã được đặt từ bước đăng ký —
 * endpoint này chỉ xác minh token và kích hoạt tài khoản.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivateAccountRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
