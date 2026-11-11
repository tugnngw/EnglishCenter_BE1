package com.sba301.englishcenter.service;

import com.sba301.englishcenter.entity.LessonProgress;

import java.util.List;
import java.util.UUID;

/**
 * Tiến độ xem video của học viên (V29).
 * <p>
 * Luật chống tua:
 * <ul>
 *   <li>Client gửi lên {@code currentTime} (giây) mỗi khoảng 5 giây + khi video kết thúc;</li>
 *   <li>BE chỉ chấp nhận mốc đã xem TĂNG LÊN, và không vượt quá
 *       {@code watchedSeconds + MAX_SEEK_GAP_SECONDS} (bù cho độ trễ mạng) — nhờ vậy
 *       học viên không thể tua vượt quá phần đã xem thật;</li>
 *   <li>Đã xem xong (mốc >= 95% thời lượng) → đánh dấu {@code completed} → tick hoàn thành.</li>
 * </ul>
 */
public interface LessonProgressService {

    /**
     * Ghi tiến độ xem video của học viên đang đăng nhập.
     *
     * @param contentId        bài học (nội dung 'lesson')
     * @param currentTime      giây hiện tại của video
     * @param durationSeconds  tổng thời lượng video (giây)
     */
    LessonProgress saveProgress(UUID contentId, Integer currentTime, Integer durationSeconds);

    /**
     * Tiến độ của học viên đang đăng nhập với một bài học.
     */
    LessonProgress getProgress(UUID contentId);

    /** Tiến độ của học viên (dùng để vẽ tick hoàn thành ở danh sách bài học). */
    List<LessonProgress> listProgress(UUID studentId, UUID courseId);

    /** Số bài học của khóa mà học viên đã xem xong. */
    long countCompletedLessons(UUID studentId, UUID courseId);

    /**
     * Số bài học (trong danh sách {@code lessonIds}) mà học viên đã xem xong.
     * <p>
     * Chỉ tính các bài học CÓ video — bài học không có video thì học viên không
     * thể xem → không thể tick hoàn thành.
     */
    long countCompletedLessons(UUID studentId, UUID courseId, List<UUID> lessonIds);
}
