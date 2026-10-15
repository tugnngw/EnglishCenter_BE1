package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateMentorRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 255, message = "Họ tên không được vượt quá 255 ký tự")
    String fullName;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    String phone;

    @NotBlank(message = "Chuyên môn không được để trống")
    @Size(max = 255, message = "Chuyên môn không được vượt quá 255 ký tự")
    String specialization;

    @NotBlank(message = "Phân khoa không được để trống")
    @Size(max = 255, message = "Phân khoa không được vượt quá 255 ký tự")
    String department;

    @Size(max = 500, message = "URL ảnh đại diện không được vượt quá 500 ký tự")
    String avatarUrl;

    String biography;

    @Positive(message = "Học phí theo giờ phải lớn hơn 0")
    BigDecimal hourlyRate;
}
