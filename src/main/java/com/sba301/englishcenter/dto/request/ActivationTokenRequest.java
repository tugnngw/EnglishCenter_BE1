package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body kiểm tra token kích hoạt tài khoản (endpoint public, dùng khi mở trang /activate). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationTokenRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
