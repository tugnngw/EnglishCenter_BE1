package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.EnrollmentResponse;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface EnrollmentService {
    boolean isEnrolled(UUID studentId, UUID courseId);
    boolean canAccessContent(UUID userId, UUID courseId);
    PageResponse<EnrollmentResponse> getMyEnrollments(Pageable pageable);
}
