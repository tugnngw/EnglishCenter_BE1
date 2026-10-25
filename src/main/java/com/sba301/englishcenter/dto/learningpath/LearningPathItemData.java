package com.sba301.englishcenter.dto.learningpath;

import com.sba301.englishcenter.common.enums.LearningPathItemType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 1 chặng trong cột JSONB learning_paths.items (TASK-D5-03).
 * title được chụp lại lúc admin tạo/sửa để hiển thị, không phải gọi sang module khác mỗi lần đọc.
 */
public record LearningPathItemData(UUID itemId, String stage, int order, LearningPathItemType type,
                                   UUID refId, String title, BigDecimal targetBand) {
}
