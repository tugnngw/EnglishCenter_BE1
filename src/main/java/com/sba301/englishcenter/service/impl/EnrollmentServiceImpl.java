package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.EnrollmentResponse;
import com.sba301.englishcenter.entity.Enrollment;
import com.sba301.englishcenter.exception.*;
import com.sba301.englishcenter.repository.*;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class EnrollmentServiceImpl implements EnrollmentService {
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final CourseRepository courses;

    public boolean isEnrolled(UUID studentId, UUID courseId) {
        return studentId != null && enrollments.existsByStudentIdAndCourse_CourseIdAndStatus(
                studentId, courseId, Enrollment.Status.active);
    }

    public boolean canAccessContent(UUID userId, UUID courseId) {
        if (userId == null) return false;
        if (isEnrolled(userId, courseId)) return true;
        var user = users.findById(userId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if ("ADMIN".equalsIgnoreCase(user.getRole().getRoleName())) return true;
        return courses.findById(courseId)
                .map(c -> c.getMentor() != null && userId.equals(c.getMentor().getUserId()))
                .orElse(false);
    }

    public PageResponse<EnrollmentResponse> getMyEnrollments(Pageable pageable) {
        UUID userId = SecurityUtils.getCurrentUserId().orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        var page = enrollments.findByStudentIdAndStatus(userId, Enrollment.Status.active, pageable);
        return new PageResponse<>(page.getNumber(), page.getSize(), page.getTotalPages(), page.getTotalElements(),
                page.map(e -> new EnrollmentResponse(e.getEnrollmentId(), e.getStudentId(), e.getCourse().getCourseId(),
                        e.getCourse().getTitle(), e.getCourse().getThumbnailUrl(), e.getEnrolledDate(), e.getStatus().name())).getContent());
    }

    @Override
    public PageResponse<EnrollmentResponse> getAllEnrollments(UUID studentId, UUID courseId, String status, Pageable pageable) {
        Specification<Enrollment> spec = buildEnrollmentSpec(studentId, courseId, status);
        Page<Enrollment> page = enrollments.findAll(spec, pageable);

        return new PageResponse<>(
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                page.getTotalElements(),
                page.map(this::toEnrollmentResponse).getContent()
        );
    }

    @Override
    public List<EnrollmentResponse> getStudentEnrollments(UUID studentId) {
        List<Enrollment> studentEnrollments = enrollments.findByStudentIdAndStatus(studentId, Enrollment.Status.active);
        return studentEnrollments.stream()
                .map(this::toEnrollmentResponse)
                .toList();
    }

    private Specification<Enrollment> buildEnrollmentSpec(UUID studentId, UUID courseId, String status) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (studentId != null) {
                predicates.add(cb.equal(root.get("studentId"), studentId));
            }

            if (courseId != null) {
                predicates.add(cb.equal(root.get("course").get("courseId"), courseId));
            }

            if (status != null && !status.isBlank()) {
                try {
                    Enrollment.Status enrollmentStatus = Enrollment.Status.valueOf(status.toLowerCase());
                    predicates.add(cb.equal(root.get("status"), enrollmentStatus));
                } catch (IllegalArgumentException e) {
                    // Invalid status - ignore filter
                }
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private EnrollmentResponse toEnrollmentResponse(Enrollment e) {
        return new EnrollmentResponse(
                e.getEnrollmentId(),
                e.getStudentId(),
                e.getCourse().getCourseId(),
                e.getCourse().getTitle(),
                e.getCourse().getThumbnailUrl(),
                e.getEnrolledDate(),
                e.getStatus().name()
        );
    }
}
