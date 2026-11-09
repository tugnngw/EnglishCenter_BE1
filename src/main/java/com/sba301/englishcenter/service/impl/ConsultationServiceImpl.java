package com.sba301.englishcenter.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.ConsultationStatus;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.NotificationType;
import com.sba301.englishcenter.common.enums.RoleName;
import com.sba301.englishcenter.dto.consultation.AvailableSlotData;
import com.sba301.englishcenter.dto.request.CreateConsultationRequest;
import com.sba301.englishcenter.dto.response.ConsultationResponse;
import com.sba301.englishcenter.dto.response.StudentOverviewResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.ConsultationRequest;
import com.sba301.englishcenter.entity.StudentProfile;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.ConsultationRequestRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.StudentProfileRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.service.ConsultationService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.util.JsonbMapper;
import com.sba301.englishcenter.util.UtcTimeUtil;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationServiceImpl implements ConsultationService {

    private static final List<ConsultationStatus> OPEN_STATUSES = List.of(ConsultationStatus.pending, ConsultationStatus.contacted);
    private static final List<String> LEARNER_ROLES = List.of(RoleName.USER.getValue(), RoleName.STUDENT.getValue());
    private static final TypeReference<List<AvailableSlotData>> SLOTS = new TypeReference<>() {
    };

    private final ConsultationRequestRepository consultationRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CertificateRepository certificateRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final NotificationService notificationService;

    // ===================== học viên =====================

    @Override
    @Transactional
    public ConsultationResponse request(UUID studentId, CreateConsultationRequest request) {
        User student = findUser(studentId);
        String phone = request.getPhone().trim();
        String note = StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null;
        String slots = JsonbMapper.write(normalizeSlots(request.getAvailableSlots()));

        // SĐT chỉ hỏi khi cần tư vấn -> lưu luôn vào hồ sơ để mentor liên hệ
        student.setPhone(phone);
        student.setLastUpdated(LocalDateTime.now());
        userRepository.save(student);

        // Đang có yêu cầu mở thì cập nhật SĐT / ghi chú, không tạo trùng
        Optional<ConsultationRequest> open = consultationRepository.findFirstByStudentIdAndStatusIn(studentId, OPEN_STATUSES);
        if (open.isPresent()) {
            ConsultationRequest existing = open.get();
            existing.setPhone(phone);
            existing.setNote(note);
            existing.setAvailableSlots(slots);
            return toResponse(consultationRepository.save(existing));
        }

        ConsultationRequest saved = consultationRepository.save(ConsultationRequest.builder()
                .studentId(studentId)
                .phone(phone)
                .note(note)
                .availableSlots(slots)
                .status(ConsultationStatus.pending)
                .build());
        log.info("Student {} requested consultation {}", studentId, saved.getRequestId());

        // Báo mọi mentor đang hoạt động, mentor nhận trước sẽ phụ trách
        String message = "Học viên " + student.getFullName() + " vừa để lại SĐT nhờ tư vấn lộ trình. "
                + "Vào mục Học viên & tư vấn để nhận yêu cầu.";
        userRepository.findAll(hasRole(List.of(RoleName.MENTOR.getValue())))
                .stream()
                .filter(m -> !Boolean.FALSE.equals(m.getIsActive()))
                .forEach(m -> notificationService.notify(m.getUserId(), NotificationType.general,
                        "Có yêu cầu tư vấn mới", message));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsultationResponse getMyLatest(UUID studentId) {
        return consultationRepository.findFirstByStudentIdOrderByCreatedAtDesc(studentId)
                .map(this::toResponse)
                .orElse(null);
    }

    // ===================== mentor =====================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConsultationResponse> getForMentor(UUID mentorId, boolean mine, Pageable pageable) {
        Page<ConsultationRequest> page = mine
                ? consultationRepository.findByMentorIdOrderByLastUpdatedDesc(mentorId, pageable)
                : consultationRepository.findByStatusOrderByCreatedAtDesc(ConsultationStatus.pending, pageable);
        return toPage(page, this::toResponse);
    }

    @Override
    @Transactional
    public ConsultationResponse claim(UUID mentorId, UUID requestId) {
        ConsultationRequest request = lock(requestId);
        if (request.getMentorId() != null && !request.getMentorId().equals(mentorId)) {
            throw new AppException(ErrorCode.CONSULTATION_TAKEN);
        }
        if (request.getStatus() == ConsultationStatus.pending) {
            request.setMentorId(mentorId);
            request.setStatus(ConsultationStatus.contacted);
            consultationRepository.save(request);
            log.info("Mentor {} claimed consultation {}", mentorId, requestId);
            notificationService.notify(request.getStudentId(), NotificationType.general, "Mentor đã nhận tư vấn",
                    "Mentor " + findUser(mentorId).getFullName()
                            + " sẽ liên hệ với bạn qua số " + request.getPhone() + " để tư vấn lộ trình.");
        }
        return toResponse(request);
    }

    @Override
    @Transactional
    public ConsultationResponse updateStatus(UUID mentorId, UUID requestId, ConsultationStatus status) {
        // "agreed" chỉ đặt khi mentor tạo gói kèm 1-1; "pending" không quay lại được
        if (status != ConsultationStatus.contacted && status != ConsultationStatus.closed) {
            throw new AppException(ErrorCode.CONSULTATION_STATUS_INVALID);
        }
        ConsultationRequest request = lock(requestId);
        if (!mentorId.equals(request.getMentorId())) {
            throw new AppException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        if (!OPEN_STATUSES.contains(request.getStatus())) {
            throw new AppException(ErrorCode.CONSULTATION_STATUS_INVALID);
        }
        request.setStatus(status);
        return toResponse(consultationRepository.save(request));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentOverviewResponse> searchStudents(String keyword, Pageable pageable) {
        Specification<User> spec = hasRole(LEARNER_ROLES);
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(root.get("phone"), like)));
        }
        Page<User> page = userRepository.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt")));
        return toPage(page, this::toOverview);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentOverviewResponse getStudent(UUID studentId) {
        User user = findUser(studentId);
        if (!LEARNER_ROLES.contains(user.getRole().getRoleName())) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return toOverview(user);
    }

    // ===================== helpers =====================

    /**
     * Kiểm tra giờ kết thúc sau giờ bắt đầu, sắp xếp theo thứ trong tuần rồi giờ bắt đầu, bỏ khung trùng hệt nhau.
     */
    private static List<AvailableSlotData> normalizeSlots(List<AvailableSlotData> slots) {
        for (AvailableSlotData slot : slots) {
            if (!LocalTime.parse(slot.to()).isAfter(LocalTime.parse(slot.from()))) {
                throw new AppException(ErrorCode.CONSULTATION_SLOT_INVALID);
            }
        }
        return slots.stream()
                .distinct()
                .sorted(Comparator.comparing(AvailableSlotData::day).thenComparing(AvailableSlotData::from))
                .toList();
    }

    /** Đọc lịch rảnh từ JSONB (trống thì danh sách rỗng). */
    private static List<AvailableSlotData> readSlots(String json) {
        return JsonbMapper.readList(json, SLOTS);
    }

    private ConsultationRequest lock(UUID requestId) {
        return consultationRepository.findWithLockByRequestId(requestId)
                .orElseThrow(() -> new AppException(ErrorCode.CONSULTATION_NOT_FOUND));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private static Specification<User> hasRole(List<String> roles) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("role").get("roleName").in(roles));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String certificateName(UUID certificateId) {
        return certificateId == null ? null
                : certificateRepository.findById(certificateId).map(Certificate::getCertificateName).orElse(null);
    }

    private StudentOverviewResponse toOverview(User user) {
        Optional<StudentProfile> profile = studentProfileRepository.findById(user.getUserId());
        UUID certId = profile.map(StudentProfile::getTargetCertificateId).orElse(null);
        Optional<ConsultationRequest> latest = consultationRepository.findFirstByStudentIdOrderByCreatedAtDesc(user.getUserId());
        return StudentOverviewResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().getRoleName())
                .targetCertificateId(certId)
                .targetCertificateName(certificateName(certId))
                .targetScore(profile.map(StudentProfile::getTargetScore).orElse(null))
                .currentLevel(profile.map(StudentProfile::getCurrentLevel).orElse(null))
                .currentScore(profile.map(StudentProfile::getCurrentScore).orElse(null))
                .levelTestCount(examAttemptRepository.countByStudent_UserIdAndExam_ExamScope(user.getUserId(), ExamScope.PRACTICE))
                .consultationStatus(latest.map(c -> c.getStatus().name()).orElse(null))
                .availableSlots(latest.map(c -> readSlots(c.getAvailableSlots())).orElse(List.of()))
                .build();
    }

    private ConsultationResponse toResponse(ConsultationRequest request) {
        User student = findUser(request.getStudentId());
        Optional<StudentProfile> profile = studentProfileRepository.findById(request.getStudentId());
        UUID certId = profile.map(StudentProfile::getTargetCertificateId).orElse(null);
        String mentorName = request.getMentorId() == null ? null
                : userRepository.findById(request.getMentorId()).map(User::getFullName).orElse(null);
        return ConsultationResponse.builder()
                .requestId(request.getRequestId())
                .studentId(request.getStudentId())
                .studentName(student.getFullName())
                .studentEmail(student.getEmail())
                .phone(request.getPhone())
                .note(request.getNote())
                .availableSlots(readSlots(request.getAvailableSlots()))
                .status(request.getStatus().name())
                .mentorId(request.getMentorId())
                .mentorName(mentorName)
                .targetCertificateId(certId)
                .targetCertificateName(certificateName(certId))
                .targetScore(profile.map(StudentProfile::getTargetScore).orElse(null))
                .currentLevel(profile.map(StudentProfile::getCurrentLevel).orElse(null))
                .currentScore(profile.map(StudentProfile::getCurrentScore).orElse(null))
                .createdAt(UtcTimeUtil.fromServerTime(request.getCreatedAt()))
                .lastUpdated(UtcTimeUtil.fromServerTime(request.getLastUpdated()))
                .build();
    }

    private static <E, R> PageResponse<R> toPage(Page<E> page, Function<E, R> mapper) {
        return PageResponse.<R>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream().map(mapper).toList())
                .build();
    }
}
