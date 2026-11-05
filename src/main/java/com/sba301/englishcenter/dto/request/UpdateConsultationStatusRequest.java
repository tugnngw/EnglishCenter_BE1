package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.ConsultationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Mentor cập nhật yêu cầu tư vấn mình phụ trách: "contacted" (đang liên hệ) hoặc "closed" (không chốt).
 * "agreed" được đặt tự động khi mentor tạo gói kèm 1-1 từ yêu cầu này.
 */
@Getter
@Setter
public class UpdateConsultationStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private ConsultationStatus status;
}
