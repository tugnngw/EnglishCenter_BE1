package com.sba301.englishcenter.dto.learningpath;

import com.sba301.englishcenter.common.enums.LearningPathStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tiến độ 1 học viên trong cột JSONB learning_paths.student_progress (TASK-D5-03).
 * Thời điểm lưu theo UTC (D5-19).
 */
public record StudentProgressData(UUID studentId, LearningPathStatus status, int progressPercent,
                                  int completedItems, LocalDateTime enrolledAt, LocalDateTime completedAt) {
}
