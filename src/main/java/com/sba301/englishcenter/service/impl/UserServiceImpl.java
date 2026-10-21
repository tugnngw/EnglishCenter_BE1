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
        log.warn("Attempt to change role for userId: {} to role: {} was blocked. Roles are immutable via manual assignment.",
                userId, request.getRoleName());
        throw new AppException(ErrorCode.ROLE_CANNOT_BE_CHANGED);
    }

    @Override
    @Transactional
    public UserResponse upgradeToStudent(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String currentRoleName = user.getRole().getRoleName();

        if ("STUDENT".equalsIgnoreCase(currentRoleName)) {
            log.info("User {} is already a STUDENT", userId);
            return toUserResponse(user);
        }

        if (!"USER".equalsIgnoreCase(currentRoleName)) {
            log.warn("Cannot upgrade user {} with role {} to STUDENT", userId, currentRoleName);
            throw new AppException(ErrorCode.FORBIDDEN_OPERATION);
        }

        Role studentRole = roleRepository.findByRoleName("STUDENT")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        user.setRole(studentRole);
        user.setLastUpdated(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        // Create StudentProfile if not present
        if (!studentProfileRepository.existsById(userId)) {
            StudentProfile studentProfile = StudentProfile.builder()
                    .user(savedUser)
                    .build();
            studentProfileRepository.save(studentProfile);
        }

        log.info("User {} successfully upgraded from USER to STUDENT upon course purchase", userId);
        return toUserResponse(savedUser);
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
