package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Học viên để lại SĐT nhờ mentor tư vấn lộ trình (đăng ký không còn ô SĐT nên chỉ hỏi ở đây).
 * SĐT được lưu luôn vào hồ sơ (users.phone).
 */
@Getter
@Setter
public class CreateConsultationRequest {

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ (VD 0912345678)")
    private String phone;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;
}
