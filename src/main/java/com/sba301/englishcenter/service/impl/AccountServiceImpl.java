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

        // Update student profile if user is a student and fields are provided
        if ("STUDENT".equals(user.getRole().getRoleName())) {
            StudentProfile profile = studentProfileRepository.findById(userId).orElse(null);
            if (profile != null) {
                boolean profileUpdated = false;

                if (request.getTargetCertificateId() != null) {
                    profile.setTargetCertificateId(request.getTargetCertificateId());
                    profileUpdated = true;
                }
                if (request.getTargetScore() != null) {
                    profile.setTargetScore(request.getTargetScore());
                    profileUpdated = true;
                }

                if (profileUpdated) {
                    profile.setLastUpdated(LocalDateTime.now());
                    studentProfileRepository.save(profile);
                }
            }
        }

        log.info("Profile updated: userId={}", userId);

        return toUserResponse(user);
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
