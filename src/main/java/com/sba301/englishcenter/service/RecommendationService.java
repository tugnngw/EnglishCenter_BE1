package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.RecommendationResponse;

import java.util.UUID;

/**
 * Gợi ý khóa học và lộ trình học cá nhân hóa cho học viên (TASK-DEV1 T8).
 */
public interface RecommendationService {

    RecommendationResponse getRecommendations(UUID userId);
}
