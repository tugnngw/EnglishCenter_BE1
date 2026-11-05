package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Nguyện vọng của người học đang đăng nhập (Learner USER / STUDENT): chứng chỉ + điểm mục tiêu + điểm hiện tại.
 * Dùng để Learner / Student chỉ thấy khóa học, lộ trình liên quan tới chứng chỉ cần đạt (không sổ hết ra).
 * Khách chưa đăng nhập, Admin, Mentor -> Optional.empty() (xem tất cả như cũ).
 */
@Component
@RequiredArgsConstructor
public class LearnerGoalResolver {

    private final StudentProfileRepository studentProfileRepository;

    public record LearnerGoal(UUID certificateId, BigDecimal targetScore, BigDecimal currentScore) {
    }

    @Transactional(readOnly = true)
    public Optional<LearnerGoal> currentLearnerGoal() {
        Optional<UserPrincipal> principal = SecurityUtils.getCurrentUserPrincipal();
        if (principal.isEmpty()) {
            return Optional.empty();
        }
        String role = principal.get().getRoleName();
        if (!RoleName.USER.getValue().equals(role) && !RoleName.STUDENT.getValue().equals(role)) {
            return Optional.empty();
        }
        Optional<StudentProfile> profile = studentProfileRepository.findById(principal.get().getUserId());
        return Optional.of(new LearnerGoal(
                profile.map(StudentProfile::getTargetCertificateId).orElse(null),
                profile.map(StudentProfile::getTargetScore).orElse(null),
                profile.map(StudentProfile::getCurrentScore).orElse(null)));
    }
}
