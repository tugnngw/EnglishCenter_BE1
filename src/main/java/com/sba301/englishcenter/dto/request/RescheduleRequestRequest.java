package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Học viên xin mentor dời 1 buổi kèm 1-1 (V28). Chỉ là yêu cầu - mentor quyết định dời hay từ chối.
 * preferredStartTime: giờ học viên mong muốn (ISO-8601 UTC, không bắt buộc).
 */
@Getter
@Setter
public class RescheduleRequestRequest {

    private Instant preferredStartTime;

    @NotBlank(message = "Hãy ghi lý do xin dời lịch")
    @Size(max = 500, message = "Lý do không được vượt quá 500 ký tự")
    private String note;
}
