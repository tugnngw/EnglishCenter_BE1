package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.response.RecommendationResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Recommendations", description = "Gợi ý khóa học & lộ trình học theo trình độ và mục tiêu (TASK-DEV1 T8)")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping({"/api/v1/me/recommendations", "/api/v1/recommendations/me"})
    @Operation(summary = "Lấy gợi ý khóa học và lộ trình phù hợp",
            description = "Dựa trên target_certificate_id, target_score, current_score, current_level của học viên")
    public ApiResponse<RecommendationResponse> getRecommendations() {
        UUID userId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return ApiResponse.success(recommendationService.getRecommendations(userId));
    }
}
