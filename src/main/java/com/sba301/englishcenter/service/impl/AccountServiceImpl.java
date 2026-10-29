package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.dto.request.UpdateProfileRequest;
import com.sba301.englishcenter.dto.response.UserResponse;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMyProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return toUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateMyProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Update basic user info
        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        user.setLastUpdated(LocalDateTime.now());
        userRepository.save(user);

        // Nguyện vọng: Learner (USER) cũng phải nhập trước khi làm Test trình độ,
        // nên tạo StudentProfile ngay khi chưa có (trước đây chỉ tạo khi lên STUDENT)
        boolean hasTarget = request.getTargetCertificateId() != null || request.getTargetScore() != null;
        if (isLearner(user) && hasTarget) {
            StudentProfile profile = studentProfileRepository.findById(userId)
                    .orElseGet(() -> StudentProfile.builder().user(user).build());
            if (request.getTargetCertificateId() != null) {
                profile.setTargetCertificateId(request.getTargetCertificateId());
            }
            if (request.getTargetScore() != null) {
                profile.setTargetScore(request.getTargetScore());
            }
            profile.setLastUpdated(LocalDateTime.now());
            studentProfileRepository.save(profile);
        }

        log.info("Profile updated: userId={}", userId);

        return toUserResponse(user);
    }

    private boolean isLearner(User user) {
        String role = user.getRole().getRoleName();
        return "STUDENT".equals(role) || "USER".equals(role);
    }

    private UserResponse toUserResponse(User user) {
        UserResponse.UserResponseBuilder builder = UserResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .role(user.getRole().getRoleName())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt());
        if (isLearner(user)) {
            studentProfileRepository.findById(user.getUserId()).ifPresent(p -> builder
                    .targetCertificateId(p.getTargetCertificateId())
                    .targetScore(p.getTargetScore())
                    .currentLevel(p.getCurrentLevel())
                    .currentScore(p.getCurrentScore()));
        }
        return builder.build();
    }
}
