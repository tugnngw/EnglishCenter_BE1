package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.UpdateProfileRequest;
import com.sba301.englishcenter.dto.response.UserResponse;

import java.util.UUID;

public interface AccountService {

    /**
     * Get current user's profile.
     *
     * @param userId Current user's ID
     * @return User profile
     */
    UserResponse getMyProfile(UUID userId);

    /**
     * Update current user's profile.
     * Updates basic info and student profile if applicable.
     *
     * @param userId Current user's ID
     * @param request Update request
     * @return Updated profile
     */
    UserResponse updateMyProfile(UUID userId, UpdateProfileRequest request);
}
