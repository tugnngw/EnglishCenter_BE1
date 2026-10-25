package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.PracticeExamRequest;
import com.sba301.englishcenter.dto.response.PracticeExamResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.PracticeExamService;
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
@RequestMapping("/api/v1/admin/practice-exams")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - AI Practice", description = "Admin quản lý kho đề luyện tập Writing / Speaking")
public class AdminPracticeExamController {

    private final PracticeExamService practiceExamService;

    @PostMapping
    @Operation(summary = "Tạo đề luyện tập")
    public ApiResponse<PracticeExamResponse> create(@Valid @RequestBody PracticeExamRequest request) {
        UUID adminId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return ApiResponse.success(practiceExamService.create(adminId, request));
    }

    @PutMapping("/{practiceExamId}")
    @Operation(summary = "Sửa đề luyện tập")
    public ApiResponse<PracticeExamResponse> update(@PathVariable UUID practiceExamId,
                                                    @Valid @RequestBody PracticeExamRequest request) {
        return ApiResponse.success(practiceExamService.update(practiceExamId, request));
    }

    @DeleteMapping("/{practiceExamId}")
    @Operation(summary = "Xóa đề luyện tập", description = "6026 nếu đề đã có bài làm")
    public ApiResponse<Void> delete(@PathVariable UUID practiceExamId) {
        practiceExamService.delete(practiceExamId);
        return ApiResponse.success();
    }
}
