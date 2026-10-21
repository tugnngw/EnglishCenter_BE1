package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.AssignRoleRequest;
import com.sba301.englishcenter.dto.request.CreateMentorRequest;
import com.sba301.englishcenter.dto.request.UpdateMentorRequest;
import com.sba301.englishcenter.dto.request.UpdateUserStatusRequest;
import com.sba301.englishcenter.dto.response.CreateMentorResponse;
import com.sba301.englishcenter.dto.response.MentorResponse;
import com.sba301.englishcenter.dto.response.RoleResponse;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.service.MentorService;
import com.sba301.englishcenter.service.RoleService;
import com.sba301.englishcenter.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Admin operations for user and mentor management")
public class AdminController {

    private final UserService userService;
    private final RoleService roleService;
    private final MentorService mentorService;

    // ============================================
    // USERS
    // ============================================

    @GetMapping("/users")
    @Operation(summary = "Get all users with filters",
            description = "Retrieve paginated list of users with optional keyword search, role filter, and active status filter")
    public PageResponse<UserResponse> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) Boolean isActive,
            @PageableDefault(size = 20) Pageable pageable) {
        return userService.getUsers(pageable, keyword, roleName, isActive);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieve detailed information of a specific user")
    public ApiResponse<UserResponse> getUserById(@PathVariable UUID userId) {
        return ApiResponse.success(userService.getUserById(userId));
    }

    @PutMapping("/users/{userId}/status")
    @Operation(summary = "Update user status",
            description = "Enable or disable user account. Admin cannot disable their own account.")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        userService.updateUserStatus(userId, request.getIsActive());
        return ApiResponse.success();
    }

    @PostMapping("/users/{userId}/roles")
    @Operation(summary = "Assign role to user",
            description = "Attempt to change user role. Roles are immutable in this system and will be rejected.")
    public ApiResponse<Void> assignRole(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request) {
        userService.assignRole(userId, request);
        return ApiResponse.success();
    }

    // ============================================
    // ROLES
    // ============================================

    @GetMapping("/roles")
    @Operation(summary = "Get all roles", description = "Retrieve list of all available roles in the system")
    public ApiResponse<List<RoleResponse>> getAllRoles() {
        return ApiResponse.success(roleService.getAllRoles());
    }

    // ============================================
    // MENTORS
    // ============================================

    @PostMapping("/mentors")
    @Operation(summary = "Create mentor account",
            description = "Create new mentor with temporary password. Returns temp password in response - DO NOT send email.")
    public ApiResponse<CreateMentorResponse> createMentor(
            @Valid @RequestBody CreateMentorRequest request) {
        return ApiResponse.success(mentorService.createMentor(request));
    }

    @PutMapping("/mentors/{mentorId}")
    @Operation(summary = "Update mentor profile", description = "Update mentor's information and profile details")
    public ApiResponse<MentorResponse> updateMentor(
            @PathVariable UUID mentorId,
            @Valid @RequestBody UpdateMentorRequest request) {
        return ApiResponse.success(mentorService.updateMentor(mentorId, request));
    }

    @DeleteMapping("/mentors/{mentorId}")
    @Operation(summary = "Revoke mentor status",
            description = "Change mentor role to STUDENT and delete mentor profile")
    public ApiResponse<Void> revokeMentor(@PathVariable UUID mentorId) {
        mentorService.revokeMentor(mentorId);
        return ApiResponse.success();
    }
}
