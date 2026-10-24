package com.sba301.englishcenter.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record EnrollmentResponse(UUID enrollmentId, UUID courseId, String courseTitle,
        String thumbnailUrl, LocalDateTime enrolledDate, String status) {}
