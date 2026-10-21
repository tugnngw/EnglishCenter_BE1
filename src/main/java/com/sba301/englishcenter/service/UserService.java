package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.AssignRoleRequest;
import com.sba301.englishcenter.dto.response.UserResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    /**
     * Get paginated list of users with optional filters.
     *
     * @param pageable Pagination parameters
     * @param keyword Search keyword (email, fullName, phone)
     * @param roleName Filter by role name
     * @param isActive Filter by active status
     * @return Paginated user list
     */
    PageResponse<UserResponse> getUsers(Pageable pageable, String keyword, String roleName, Boolean isActive);

    /**
     * Get user by ID.
     *
     * @param userId User ID
     * @return User details
     */
    UserResponse getUserById(UUID userId);

    /**
     * Update user active status.
     * Admin cannot disable their own account.
     *
     * @param userId User ID
     * @param isActive New active status
     */
    void updateUserStatus(UUID userId, Boolean isActive);

    /**
     * Assign role to user.
     * Roles are immutable; this operation is blocked by business policy.
     *
     * @param userId User ID
     * @param request Role assignment request
     */
    void assignRole(UUID userId, AssignRoleRequest request);

    /**
     * Upgrade a registered USER to STUDENT upon course purchase.
     * Automatically creates StudentProfile.
     *
     * @param userId User ID
     * @return Updated user response
     */
    UserResponse upgradeToStudent(UUID userId);
}
