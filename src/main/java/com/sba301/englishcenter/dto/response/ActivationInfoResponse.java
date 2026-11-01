package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Kết quả kiểm tra token kích hoạt — trang /activate hiển thị "Kích hoạt tài khoản cho {fullName}".
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivationInfoResponse {

    private String email;
    private String fullName;
}
