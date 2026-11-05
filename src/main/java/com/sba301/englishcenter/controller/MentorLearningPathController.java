package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.LearningPathRequest;
import com.sba301.englishcenter.dto.response.LearningPathResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.LearningPathService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Mentor tạo lộ trình học (VD lộ trình 12 buổi nâng band) để dùng khi tư vấn và tạo gói kèm 1-1.
 * Mentor chỉ sửa / xóa lộ trình do mình tạo. Xem danh sách dùng API public GET /api/v1/learning-paths.
 */
@RestController
@RequestMapping("/api/v1/mentor/learning-paths")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
@Tag(name = "Mentor - Learning Path", description = "Mentor tạo / sửa / xóa lộ trình của mình")
public class MentorLearningPathController {

    private final LearningPathService learningPathService;

    @PostMapping
    @Operation(summary = "Mentor tạo lộ trình", description = "Giống Admin; totalSessions = số buổi kèm 1-1 của lộ trình")
    public ApiResponse<LearningPathResponse> create(@Valid @RequestBody LearningPathRequest request) {
        return ApiResponse.success(learningPathService.create(currentUserId(), request));
    }

    @PutMapping("/{learningPathId}")
    @Operation(summary = "Mentor sửa lộ trình của mình")
    public ApiResponse<LearningPathResponse> update(@PathVariable UUID learningPathId,
                                                    @Valid @RequestBody LearningPathRequest request) {
        learningPathService.checkCanManage(currentUserId(), false, learningPathId);
        return ApiResponse.success(learningPathService.update(learningPathId, request));
    }

    @DeleteMapping("/{learningPathId}")
    @Operation(summary = "Mentor xóa lộ trình của mình", description = "6022 nếu đã có học viên ghi danh")
    public ApiResponse<Void> delete(@PathVariable UUID learningPathId) {
        learningPathService.checkCanManage(currentUserId(), false, learningPathId);
        learningPathService.delete(learningPathId);
        return ApiResponse.success();
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
