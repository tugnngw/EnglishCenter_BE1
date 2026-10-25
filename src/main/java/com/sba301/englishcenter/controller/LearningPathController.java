package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.LearningPathProgressResponse;
import com.sba301.englishcenter.dto.response.LearningPathResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.LearningPathService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Learning Path", description = "Lộ trình học theo chứng chỉ và tiến độ học viên (TASK-D5-03)")
public class LearningPathController {

    private final LearningPathService learningPathService;

    @GetMapping("/learning-paths")
    @Operation(summary = "Danh sách lộ trình (public)", description = "Lọc theo certificateId nếu có")
    public PageResponse<LearningPathResponse> getLearningPaths(
            @RequestParam(required = false) UUID certificateId,
            @PageableDefault(size = 10) Pageable pageable) {
        return learningPathService.getLearningPaths(certificateId, pageable);
    }

    @GetMapping("/learning-paths/{learningPathId}")
    @Operation(summary = "Chi tiết lộ trình (public)")
    public ApiResponse<LearningPathResponse> getLearningPath(@PathVariable UUID learningPathId) {
        return ApiResponse.success(learningPathService.getLearningPath(learningPathId));
    }

    @PostMapping("/users/me/learning-paths/{learningPathId}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Ghi danh lộ trình", description = "Gọi lại nhiều lần vẫn chỉ ghi danh 1 lần; trả về tiến độ hiện tại")
    public ApiResponse<LearningPathProgressResponse> enroll(@PathVariable UUID learningPathId) {
        return ApiResponse.success(learningPathService.enroll(currentUserId(), learningPathId));
    }

    @GetMapping("/users/me/learning-paths/progress")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Tiến độ lộ trình của tôi",
            description = "COURSE: đã ghi danh khóa; EXAM: có lượt thi đậu; PRACTICE_EXAM: AI chấm đạt targetBand")
    public ApiResponse<List<LearningPathProgressResponse>> getMyProgress() {
        return ApiResponse.success(learningPathService.getMyProgress(currentUserId()));
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
