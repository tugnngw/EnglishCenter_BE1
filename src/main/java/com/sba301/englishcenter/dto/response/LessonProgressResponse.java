package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tiến độ xem video của học viên với 1 bài học (V29).
 * <p>
 * {@code watchedSeconds} là MỐC giây đã xem tối đa — client dùng để chặn tua:
 * không cho phát quá {@code watchedSeconds + sai số cho phép}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LessonProgressResponse {

    UUID contentId;
    UUID studentId;

    /** Mốc giây đã xem tối đa (chống tua). */
    Integer watchedSeconds;

    /** Tổng thời lượng video (giây). */
    Integer durationSeconds;

    /** Đã xem xong → tick "hoàn thành". */
    Boolean completed;

    LocalDateTime completedAt;
}
