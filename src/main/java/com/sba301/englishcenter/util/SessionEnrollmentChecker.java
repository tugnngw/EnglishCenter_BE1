package com.sba301.englishcenter.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

/** Entry point shared with Course Content, Exam and Booking. */
@Component
@lombok.RequiredArgsConstructor
public class SessionEnrollmentChecker {
    private final com.sba301.englishcenter.service.EnrollmentService enrollments;

    public boolean isEnrolled(UUID userId, UUID courseId) {
        return enrollments.isEnrolled(userId, courseId);
    }

    public boolean canAccessContent(UUID userId, UUID courseId) {
        return enrollments.canAccessContent(userId, courseId);
    }
}
