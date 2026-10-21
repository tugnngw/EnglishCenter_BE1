package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.AssignRoleRequest;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.entity.Role;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.MentorProfileRepository;
import com.sba301.englishcenter.repository.RoleRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.UserService;
import com.sba301.englishcenter.specification.UserSpecification;
import com.sba301.englishcenter.util.SessionVariableHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final SessionVariableHelper sessionVariableHelper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(Pageable pageable, String keyword, String roleName, Boolean isActive) {
        Specification<User> spec = UserSpecification.filterUsers(keyword, roleName, isActive);
        Page<User> userPage = userRepository.findAll(spec, pageable);

        return PageResponse.<UserResponse>builder()
                .currentPage(userPage.getNumber())
                .pageSize(userPage.getSize())
                .totalPages(userPage.getTotalPages())
                .totalElements(userPage.getTotalElements())
                .data(userPage.getContent().stream()
                        .map(this::toUserResponse)
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return toUserResponse(user);
    }

    @Override
    @Transactional
    public void updateUserStatus(UUID userId, Boolean isActive) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Admin cannot disable their own account
        if (userId.equals(currentUserId) && Boolean.FALSE.equals(isActive)) {
            throw new AppException(ErrorCode.CANNOT_DISABLE_SELF);
        }

        user.setIsActive(isActive);
        user.setLastUpdated(LocalDateTime.now());
        userRepository.save(user);

        log.info("Updated user status: userId={}, isActive={}", userId, isActive);
    }

    @Override
    @Transactional
    public void assignRole(UUID userId, AssignRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if ("MENTOR".equalsIgnoreCase(request.getRoleName())) {
            throw new AppException(ErrorCode.USE_CREATE_MENTOR_ENDPOINT);
        }

        Role newRole = roleRepository.findByRoleName(request.getRoleName())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        String currentRoleName = user.getRole().getRoleName();

        // Check if user already has this role
        if (currentRoleName.equals(request.getRoleName())) {
            throw new AppException(ErrorCode.USER_ALREADY_HAS_ROLE);
        }

        // Cannot remove last ADMIN
        if ("ADMIN".equals(currentRoleName)) {
            long adminCount = userRepository.countByRole_RoleName("ADMIN");
            if (adminCount <= 1) {
                throw new AppException(ErrorCode.CANNOT_REMOVE_LAST_ADMIN);
            }
        }

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Set session variable for trigger validation
        sessionVariableHelper.setCurrentUserId(currentUserId);

        // Update role
        user.setRole(newRole);
        user.setLastUpdated(LocalDateTime.now());

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Role assignment rejected by trigger: {}", ex.getMessage());
            if (ex.getMessage() != null && ex.getMessage().contains("Only admins")) {
                throw new AppException(ErrorCode.FORBIDDEN_OPERATION);
            }
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // Handle profile creation/deletion based on new role
        handleProfileTransition(user, currentRoleName, request.getRoleName());

        log.info("Assigned role to user: userId={}, oldRole={}, newRole={}",
                userId, currentRoleName, request.getRoleName());
    }

    /**
     * Handle profile table transitions when role changes.
     */
    private void handleProfileTransition(User user, String oldRole, String newRole) {
        UUID userId = user.getUserId();

        // Remove old profile
        switch (oldRole) {
            case "MENTOR" -> mentorProfileRepository.deleteById(userId);
            case "STUDENT" -> studentProfileRepository.deleteById(userId);
            default -> {
                // ADMIN has no profile
            }
        }

        // Create new profile
        switch (newRole) {
            case "MENTOR" -> throw new AppException(ErrorCode.USE_CREATE_MENTOR_ENDPOINT);
            case "STUDENT" -> {
                StudentProfile studentProfile = StudentProfile.builder()
                        .user(user)
                        .build();
                studentProfileRepository.save(studentProfile);
            }
            default -> {
                // ADMIN has no profile
            }
        }
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().getRoleName())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
