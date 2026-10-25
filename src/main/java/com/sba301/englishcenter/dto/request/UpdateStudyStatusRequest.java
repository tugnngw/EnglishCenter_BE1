package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.StudyStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Học viên đánh dấu lịch học: done (đã học) / skipped (bỏ qua) / cancelled (hủy) / pending (mở lại).
 */
@Getter
@Setter
public class UpdateStudyStatusRequest {

    @NotNull(message = "Trạng thái không được để trống")
    private StudyStatus status;
}
