package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.SaveLessonProgressRequest;
import com.sba301.englishcenter.dto.response.LessonProgressResponse;
import com.sba301.englishcenter.entity.LessonProgress;
import com.sba301.englishcenter.service.LessonProgressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Tiến độ xem video bài học (V29).
 * <ul>
 *   <li>{@code POST /api/v1/lesson-progress/{contentId}} — học viên báo tiến độ
 *       (mỗi ~5 giây + khi kết thúc). BE chống tua và đánh dấu hoàn thành;</li>
 *   <li>{@code GET /api/v1/lesson-progress/{contentId}} — lấy tiến độ hiện tại
 *       (client dùng mốc đã xem để chặn tua).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/lesson-progress")
@RequiredArgsConstructor
@Tag(name = "Lesson progress", description = "Video watch progress & anti-seeking")
public class LessonProgressController {

    private final LessonProgressService lessonProgressService;

    @PostMapping("/{contentId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'USER')")
    @Operation(summary = "Save video watch progress",
            description = "Báo tiến độ xem video. BE chỉ nhận mốc tăng dần và không vượt quá "
                    + "phần đã xem (chống tua). Đạt 95% thời lượng → completed = true (tick hoàn thành).")
    public ApiResponse<LessonProgressResponse> save(
            @PathVariable UUID contentId,
            @Valid @RequestBody SaveLessonProgressRequest request) {
        LessonProgress progress = lessonProgressService.saveProgress(
                contentId, request.getCurrentTime(), request.getDurationSeconds());
        return ApiResponse.success(toResponse(progress));
    }

    @GetMapping("/{contentId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'USER')")
    @Operation(summary = "Get my video watch progress",
            description = "Trả về mốc giây đã xem tối đa + cờ hoàn thành của học viên đang đăng nhập.")
    public ApiResponse<LessonProgressResponse> get(@PathVariable UUID contentId) {
        return ApiResponse.success(toResponse(lessonProgressService.getProgress(contentId)));
    }

    private LessonProgressResponse toResponse(LessonProgress progress) {
        return LessonProgressResponse.builder()
                .contentId(progress.getContentId())
                .studentId(progress.getStudent() != null
                        ? progress.getStudent().getUserId() : null)
                .watchedSeconds(progress.getWatchedSeconds())
                .durationSeconds(progress.getDurationSeconds())
                .completed(Boolean.TRUE.equals(progress.getCompleted()))
                .completedAt(progress.getCompletedAt())
                .build();
    }
}
