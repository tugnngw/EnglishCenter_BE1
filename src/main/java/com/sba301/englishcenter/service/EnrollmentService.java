package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.EnrollmentResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface EnrollmentService {
    boolean isEnrolled(UUID studentId, UUID courseId);
    boolean canAccessContent(UUID userId, UUID courseId);
    PageResponse<EnrollmentResponse> getMyEnrollments(Pageable pageable);

    /**
     * Admin: Get all enrollments with optional filters
     * @param studentId Filter by student (optional)
     * @param courseId Filter by course (optional)
     * @param status Filter by status (optional)
     * @param pageable Pagination parameters
     * @return Paginated enrollments
     */
    PageResponse<EnrollmentResponse> getAllEnrollments(UUID studentId, UUID courseId, String status, Pageable pageable);

    /**
     * Get active enrollments for a specific student
     * @param studentId Student user ID
     * @return List of active enrollments
     */
    List<EnrollmentResponse> getStudentEnrollments(UUID studentId);
}
