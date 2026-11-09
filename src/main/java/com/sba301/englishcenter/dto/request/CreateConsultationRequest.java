package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.dto.consultation.AvailableSlotData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Học viên để lại SĐT nhờ mentor tư vấn lộ trình (đăng ký không còn ô SĐT nên chỉ hỏi ở đây).
 * SĐT được lưu luôn vào hồ sơ (users.phone).
 * availableSlots (V26): lịch rảnh hằng tuần, mentor dựa vào đó xếp các buổi của gói kèm 1-1.
 */
@Getter
@Setter
public class CreateConsultationRequest {

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Số điện thoại không hợp lệ (VD 0912345678)")
    private String phone;

    @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
    private String note;

    @NotEmpty(message = "Hãy chọn ít nhất 1 khung giờ rảnh")
    @Size(max = 21, message = "Tối đa 21 khung giờ rảnh")
    private List<@Valid @NotNull AvailableSlotData> availableSlots;
}
