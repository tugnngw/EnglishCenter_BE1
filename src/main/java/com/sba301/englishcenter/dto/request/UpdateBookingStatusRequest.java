package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.BookingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Mentor cập nhật kết quả buổi học sau giờ bắt đầu (TASK D5-11).
 * status chỉ nhận "completed" (đã học) hoặc "no_show" (học viên vắng mặt).
 */
@Getter
@Setter
public class UpdateBookingStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private BookingStatus status;
}
