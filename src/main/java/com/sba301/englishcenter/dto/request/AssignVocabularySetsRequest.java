package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Gán / cập nhật danh sách bộ từ vựng chung cho một khóa học.
 * <p>
 * Gửi TOÀN BỘ danh sách bộ đang gắn với khóa học — những bộ có trong
 * {@code setIds} sẽ được gán, những bộ không có trong danh sách sẽ được gỡ.
 * Đây là mô hình "tick chọn" đúng như yêu cầu nghiệp vụ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignVocabularySetsRequest {

    /**
     * Danh sách id bộ từ vựng chung được gán vào khóa học.
     * <p>
     * Gửi mảng rỗng = gỡ toàn bộ bộ đang gán khỏi khóa học.
     */
    @NotNull(message = "Danh sách bộ từ vựng không được null")
    @Builder.Default
    List<UUID> setIds = new ArrayList<>();
}
