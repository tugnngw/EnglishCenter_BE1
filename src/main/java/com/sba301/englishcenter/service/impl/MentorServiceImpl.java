package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.CreateMentorRequest;
import com.sba301.englishcenter.dto.request.UpdateMentorRequest;
import com.sba301.englishcenter.dto.response.CreateMentorResponse;
import com.sba301.englishcenter.dto.response.MentorResponse;
import com.sba301.englishcenter.dto.response.MentorStatsResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.MentorProfile;
import com.sba301.englishcenter.entity.Role;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.MentorProfileRepository;
import com.sba301.englishcenter.repository.RoleRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.EmailService;
import com.sba301.englishcenter.service.MentorService;
import com.sba301.englishcenter.specification.MentorSpecification;
import com.sba301.englishcenter.util.SessionVariableHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorServiceImpl implements MentorService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CertificateRepository certificateRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionVariableHelper sessionVariableHelper;
    private final EmailService emailService;

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Số ngày tính là "mentor mới" trên trang quản lý */
    private static final int NEW_MENTOR_DAYS = 30;
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final String ALL_CHARS = UPPERCASE + LOWERCASE + DIGITS + SPECIAL;

    @Override
    @Transactional
    public CreateMentorResponse createMentor(CreateMentorRequest request) {
        // Check email uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        // Get MENTOR role
        Role mentorRole = roleRepository.findByRoleName("MENTOR")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        // Generate temporary password
        String tempPassword = generateRandomPassword();

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Set session variable for trigger validation
        sessionVariableHelper.setCurrentUserId(currentUserId);

        // Create user with MENTOR role
        User mentor = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(tempPassword))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .isActive(true)
                .role(mentorRole)
                .failedLoginAttempts(0)
                .build();

        try {
            mentor = userRepository.saveAndFlush(mentor);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Mentor creation rejected by trigger: {}", ex.getMessage());
            if (ex.getMessage() != null && ex.getMessage().contains("Only admins")) {
                throw new AppException(ErrorCode.FORBIDDEN_OPERATION);
            }
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // Create mentor profile
        MentorProfile profile = MentorProfile.builder()
                .user(mentor)
                .mentorCreatedBy(currentUserId)
                .specialization(request.getSpecialization())
                .department(request.getDepartment())
                .avatarUrl(request.getAvatarUrl())
                .biography(request.getBiography())
                .hourlyRate(request.getHourlyRate())
                .ratingAverage(BigDecimal.ZERO)
                .mentorRegisteredAt(LocalDateTime.now())
                .build();

        mentorProfileRepository.save(profile);

        // Gửi mật khẩu tạm trực tiếp về email mentor. Admin không được thấy mật khẩu để đảm bảo bảo mật.
        try {
            emailService.sendMentorWelcomeEmail(mentor.getEmail(), mentor.getFullName(), tempPassword);
        } catch (Exception e) {
            log.warn("Failed to send welcome email to mentor {}: {}", mentor.getEmail(), e.getMessage());
        }

        log.info("Mentor created: email={}", mentor.getEmail());

        return CreateMentorResponse.builder()
                .mentor(toMentorResponse(mentor, profile))
                .note("Mật khẩu đăng nhập ban đầu đã được hệ thống gửi trực tiếp tới email của mentor.")
                .build();
    }

    @Override
    @Transactional
    public MentorResponse updateMentor(UUID mentorId, UpdateMentorRequest request) {
        User user = userRepository.findById(mentorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Verify user is a mentor
        if (!"MENTOR".equals(user.getRole().getRoleName())) {
            throw new AppException(ErrorCode.MENTOR_PROFILE_NOT_FOUND);
        }

        MentorProfile profile = mentorProfileRepository.findById(mentorId)
                .orElseThrow(() -> new AppException(ErrorCode.MENTOR_PROFILE_NOT_FOUND));

        // Update user basic info
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setLastUpdated(LocalDateTime.now());
        userRepository.save(user);

        // Update mentor profile
        profile.setSpecialization(request.getSpecialization());
        profile.setDepartment(request.getDepartment());
        profile.setAvatarUrl(request.getAvatarUrl());
        profile.setBiography(request.getBiography());
        if (request.getHourlyRate() != null) {
            profile.setHourlyRate(request.getHourlyRate());
        }
        profile.setLastUpdated(LocalDateTime.now());
        mentorProfileRepository.save(profile);

        log.info("Mentor updated: mentorId={}", mentorId);

        return toMentorResponse(user, profile);
    }

    @Override
    @Transactional
    public void revokeMentor(UUID mentorId) {
        User user = userRepository.findById(mentorId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Verify user is a mentor
        if (!"MENTOR".equals(user.getRole().getRoleName())) {
            throw new AppException(ErrorCode.MENTOR_PROFILE_NOT_FOUND);
        }

        // Get STUDENT role
        Role studentRole = roleRepository.findByRoleName("STUDENT")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Set session variable for trigger validation
        sessionVariableHelper.setCurrentUserId(currentUserId);

        // Change role to STUDENT
        user.setRole(studentRole);
        user.setLastUpdated(LocalDateTime.now());

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Mentor revocation rejected by trigger: {}", ex.getMessage());
            if (ex.getMessage() != null && ex.getMessage().contains("Only admins")) {
                throw new AppException(ErrorCode.FORBIDDEN_OPERATION);
            }
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }

        // Delete mentor profile
        mentorProfileRepository.deleteById(mentorId);

        // Create student profile
        StudentProfile studentProfile = StudentProfile.builder()
                .user(user)
                .build();
        studentProfileRepository.save(studentProfile);

        log.info("Mentor revoked: mentorId={}, changed to STUDENT", mentorId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MentorResponse> getMentors(Pageable pageable, String specialization,
                                                    BigDecimal minRate, BigDecimal maxRate) {
        Specification<MentorProfile> spec = MentorSpecification.filterMentors(specialization, minRate, maxRate);
        Page<MentorProfile> mentorPage = mentorProfileRepository.findAll(spec, pageable);

        return PageResponse.<MentorResponse>builder()
                .currentPage(mentorPage.getNumber())
                .pageSize(mentorPage.getSize())
                .totalPages(mentorPage.getTotalPages())
                .totalElements(mentorPage.getTotalElements())
                .data(mentorPage.getContent().stream()
                        .map(profile -> toMentorResponse(profile.getUser(), profile))
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MentorResponse> getMentorsForAdmin(Pageable pageable, String keyword, String department,
                                                           String specialization, Boolean isActive) {
        Specification<MentorProfile> spec =
                MentorSpecification.filterMentorsForAdmin(keyword, department, specialization, isActive);
        Page<MentorProfile> mentorPage = mentorProfileRepository.findAll(spec, pageable);

        return PageResponse.<MentorResponse>builder()
                .currentPage(mentorPage.getNumber())
                .pageSize(mentorPage.getSize())
                .totalPages(mentorPage.getTotalPages())
                .totalElements(mentorPage.getTotalElements())
                .data(mentorPage.getContent().stream()
                        .map(profile -> toMentorResponse(profile.getUser(), profile))
                        .toList())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MentorStatsResponse getMentorStats() {
        List<MentorProfile> allProfiles = mentorProfileRepository.findAll();

        // Lọc mentor thật (có role MENTOR)
        List<MentorProfile> mentorProfiles = allProfiles.stream()
                .filter(p -> "MENTOR".equals(p.getUser().getRole().getRoleName()))
                .toList();

        long total = mentorProfiles.size();
        long active = mentorProfiles.stream()
                .filter(p -> Boolean.TRUE.equals(p.getUser().getIsActive()))
                .count();
        long inactive = total - active;

        LocalDateTime since = LocalDateTime.now().minusDays(NEW_MENTOR_DAYS);
        long newLast30Days = mentorProfiles.stream()
                .filter(p -> {
                    LocalDateTime joined = p.getMentorRegisteredAt() != null
                            ? p.getMentorRegisteredAt()
                            : p.getCreatedAt();
                    return joined != null && !joined.isBefore(since);
                })
                .count();

        List<String> departments = mentorProfiles.stream()
                .map(MentorProfile::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        // Lấy danh mục chứng chỉ từ bảng certificates trong DB
        List<String> specializations = certificateRepository.findAll().stream()
                .map(Certificate::getCertificateName)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        // Dự phòng: nếu bảng certificates chưa có dữ liệu thì lấy từ specialization hiện có của mentor
        if (specializations.isEmpty()) {
            specializations = mentorProfiles.stream()
                    .map(MentorProfile::getSpecialization)
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        return MentorStatsResponse.builder()
                .total(total)
                .active(active)
                .inactive(inactive)
                .newLast30Days(newLast30Days)
                .departments(departments)
                .specializations(specializations)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MentorResponse getMentorById(UUID mentorId) {
        MentorProfile profile = mentorProfileRepository.findById(mentorId)
                .orElseThrow(() -> new AppException(ErrorCode.MENTOR_PROFILE_NOT_FOUND));

        User user = profile.getUser();

        // Verify user is still active and is a mentor
        if (!Boolean.TRUE.equals(user.getIsActive()) || !"MENTOR".equals(user.getRole().getRoleName())) {
            throw new AppException(ErrorCode.MENTOR_PROFILE_NOT_FOUND);
        }

        return toMentorResponse(user, profile);
    }

    /**
     * Generate a random password with 12 characters:
     * - At least 1 uppercase letter
     * - At least 1 lowercase letter
     * - At least 1 digit
     * - At least 1 special character
     */
    private String generateRandomPassword() {
        StringBuilder password = new StringBuilder(12);

        // Ensure at least one character from each category
        password.append(UPPERCASE.charAt(RANDOM.nextInt(UPPERCASE.length())));
        password.append(LOWERCASE.charAt(RANDOM.nextInt(LOWERCASE.length())));
        password.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        password.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));

        // Fill remaining 8 characters randomly
        for (int i = 0; i < 8; i++) {
            password.append(ALL_CHARS.charAt(RANDOM.nextInt(ALL_CHARS.length())));
        }

        // Shuffle the password to avoid predictable patterns
        return shuffleString(password.toString());
    }

    private String shuffleString(String input) {
        char[] chars = input.toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }

    private MentorResponse toMentorResponse(User user, MentorProfile profile) {
        return MentorResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .specialization(profile.getSpecialization())
                .department(profile.getDepartment())
                .avatarUrl(profile.getAvatarUrl())
                .biography(profile.getBiography())
                .hourlyRate(profile.getHourlyRate())
                .ratingAverage(profile.getRatingAverage())
                .mentorRegisteredAt(profile.getMentorRegisteredAt())
                .createdAt(profile.getCreatedAt())
                .lastUpdated(profile.getLastUpdated())
                .build();
    }
}
