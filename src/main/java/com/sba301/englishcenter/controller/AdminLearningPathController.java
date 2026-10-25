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

@RestController
@RequestMapping("/api/v1/admin/learning-paths")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Learning Path", description = "Admin quản lý lộ trình học qua Swagger (TASK-D5-03)")
public class AdminLearningPathController {

    private final LearningPathService learningPathService;

    @PostMapping
    @Operation(summary = "Tạo lộ trình",
            description = "items[].type COURSE / EXAM / PRACTICE_EXAM, refId là id tương ứng; targetBand chỉ cho PRACTICE_EXAM")
    public ApiResponse<LearningPathResponse> create(@Valid @RequestBody LearningPathRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return ApiResponse.success(learningPathService.create(adminId, request));
    }

    @PutMapping("/{learningPathId}")
    @Operation(summary = "Sửa lộ trình", description = "Thay toàn bộ danh sách chặng; tiến độ học viên được tính lại khi họ xem")
    public ApiResponse<LearningPathResponse> update(@PathVariable UUID learningPathId,
                                                    @Valid @RequestBody LearningPathRequest request) {
        return ApiResponse.success(learningPathService.update(learningPathId, request));
    }

    @DeleteMapping("/{learningPathId}")
    @Operation(summary = "Xóa lộ trình", description = "6022 nếu đã có học viên ghi danh")
    public ApiResponse<Void> delete(@PathVariable UUID learningPathId) {
        learningPathService.delete(learningPathId);
        return ApiResponse.success();
    }
}
