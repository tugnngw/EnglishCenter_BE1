package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.LessonProgress;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.LessonProgressRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
@Service
@RequiredArgsConstructor
@Slf4j
public class LessonProgressService {

    /** Ngưỡng xem xong: 95% thời lượng video. */
    static final double COMPLETE_RATIO = 0.95;

    /**
     * Sai số cho phép giữa mốc đã xem và currentTime client gửi lên (giây).
     * <p>
     * Nhờ đó client có thể báo trễ vài giây (buffer) mà không bị từ chối, nhưng vẫn không
     * thể nhảy tới cuối video ngay lập tức.
     */
    static final int MAX_SEEK_GAP_SECONDS = 10;

    /** Bước nhảy tối thiểu để coi là "đã xem" (tránh spam). */
    private static final int MIN_PROGRESS_STEP_SECONDS = 1;

    private final LessonProgressRepository progressRepository;
    private final CourseContentRepository courseContentRepository;
    private final UserRepository userRepository;

    /**
     * Ghi tiến độ xem video của học viên đang đăng nhập.
     *
     * @param contentId        bài học (nội dung 'lesson')
     * @param currentTime      giây hiện tại của video
     * @param durationSeconds  tổng thời lượng video (giây)
     */
    @Transactional
    public LessonProgress saveProgress(UUID contentId, Integer currentTime, Integer durationSeconds) {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        CourseContent lesson = courseContentRepository.findById(contentId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
        if (lesson.getContentType() != ContentType.LESSON) {
            throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE,
                    "Tiến độ chỉ dùng cho bài học (lesson)");
        }

        int watched = Math.max(0, currentTime == null ? 0 : currentTime);
        int duration = Math.max(0, durationSeconds == null ? 0 : durationSeconds);

        LessonProgress progress = progressRepository.findByContent_ContentIdAndStudent_UserId(contentId, studentId)
                .orElseGet(() -> LessonProgress.builder()
                        .content(lesson)
                        .student(userRepository.getReferenceById(studentId))
                        .watchedSeconds(0)
                        .durationSeconds(0)
                        .completed(false)
                        .build());

        int previous = progress.getWatchedSeconds() == null ? 0 : progress.getWatchedSeconds();

        // Chống tua: mốc mới không được vượt quá phần đã xem (cộng sai số cho phép).
        int maxAllowed = previous + MAX_SEEK_GAP_SECONDS;
        int effective = Math.min(watched, Math.max(previous, maxAllowed));
        if (effective - previous < MIN_PROGRESS_STEP_SECONDS && effective == previous) {
            // Không có tiến triển mới → giữ nguyên mốc cũ.
            effective = previous;
        }

        progress.setWatchedSeconds(effective);
        if (duration > 0) {
            progress.setDurationSeconds(duration);
        }

        boolean justCompleted = false;
        if (!Boolean.TRUE.equals(progress.getCompleted())
                && progress.getDurationSeconds() > 0
                && effective >= progress.getDurationSeconds() * COMPLETE_RATIO) {
            progress.setCompleted(true);
            progress.setCompletedAt(LocalDateTime.now());
            justCompleted = true;
        }

        progress = progressRepository.save(progress);
        if (justCompleted) {
            log.info("Học viên {} đã xem xong bài học {}", studentId, contentId);
        }
        return progress;
    }

    /**
     * Tiến độ của học viên đang đăng nhập với một bài học.
     */
    @Transactional(readOnly = true)
    public LessonProgress getProgress(UUID contentId) {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return progressRepository.findByContent_ContentIdAndStudent_UserId(contentId, studentId)
                .orElseThrow(() -> new AppException(ErrorCode.LESSON_PROGRESS_NOT_FOUND));
    }

    /** Tiến độ của học viên (dùng để vẽ tick hoàn thành ở danh sách bài học). */
    @Transactional(readOnly = true)
    public List<LessonProgress> listProgress(UUID studentId, UUID courseId) {
        if (studentId == null || courseId == null) {
            return List.of();
        }
        return progressRepository.findByStudent_UserIdAndContent_Course_CourseId(studentId, courseId);
    }

    /** Số bài học của khóa mà học viên đã xem xong. */
    @Transactional(readOnly = true)
    public long countCompletedLessons(UUID studentId, UUID courseId) {
        if (studentId == null || courseId == null) {
            return 0;
        }
        return progressRepository.countByStudent_UserIdAndContent_Course_CourseIdAndCompletedTrue(studentId, courseId);
    }

    /**
     * Số bài học (trong danh sách {@code lessonIds}) mà học viên đã xem xong.
     * <p>
     * Chỉ tính các bài học CÓ video — bài học không có video thì học viên không
     * thể xem → không thể tick hoàn thành.
     */
    @Transactional(readOnly = true)
    public long countCompletedLessons(UUID studentId, UUID courseId, List<UUID> lessonIds) {
        if (studentId == null || courseId == null || lessonIds == null || lessonIds.isEmpty()) {
            return 0;
        }
        Set<UUID> target = new HashSet<>(lessonIds);
        long completed = 0;
        for (LessonProgress progress
                : progressRepository.findByStudent_UserIdAndContent_Course_CourseId(studentId, courseId)) {
            if (Boolean.TRUE.equals(progress.getCompleted())
                    && target.contains(progress.getContentId())) {
                completed++;
            }
        }
        return completed;
    }
}
