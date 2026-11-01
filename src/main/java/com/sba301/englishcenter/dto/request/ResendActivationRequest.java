package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Yêu cầu gửi lại email kích hoạt tài khoản (khi link cũ đã mất/hết hạn). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResendActivationRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
}
