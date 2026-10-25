package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Hủy lịch hẹn (TASK D5-09). Lý do không bắt buộc.
 */
@Getter
@Setter
public class CancelBookingRequest {

    @Size(max = 500, message = "Lý do hủy không được vượt quá 500 ký tự")
    private String reason;
}
