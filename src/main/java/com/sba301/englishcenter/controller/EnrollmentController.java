package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.*;
import com.sba301.englishcenter.dto.response.EnrollmentResponse;
import com.sba301.englishcenter.entity.PaymentTransaction;
import com.sba301.englishcenter.exception.*;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor @Tag(name = "Enrollment", description = "Purchased courses, enrollment status and personal payment history")
@RequestMapping("/api/v1")
public class EnrollmentController {
    private final EnrollmentService enrollments;
    private final PaymentService payments;
    private final CourseRepository courses;
    public record EnrollmentStatusResponse(UUID courseId, boolean enrolled) {}

    @GetMapping("/me/enrollments")
    @Operation(summary = "Get my enrolled courses",
            description = "Retrieve the current user's paginated active enrollments with course details for My Courses.")
    public ApiResponse<PageResponse<EnrollmentResponse>> myCourses(
            @PageableDefault(sort = "enrolledDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(enrollments.getMyEnrollments(pageable));
    }
    @GetMapping({"/me/enrollments/{courseId}", "/courses/{courseId}/enrollment-status"})
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check my course enrollment",
            description = "Check whether the authenticated user has an active enrollment for the specified course. Does not check another user's enrollment.")
    public ApiResponse<EnrollmentStatusResponse> status(@PathVariable UUID courseId) {
        UUID user = SecurityUtils.getCurrentUserId().orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        if (!courses.existsById(courseId)) throw new AppException(ErrorCode.COURSE_NOT_FOUND);
        return ApiResponse.success(new EnrollmentStatusResponse(courseId, enrollments.isEnrolled(user, courseId)));
    }
    @GetMapping("/me/payments")
    @Operation(summary = "Get my payment history",
            description = "Retrieve the current user's paginated recorded payment transactions. Pending orders and transfers requiring review are available through the order APIs.")
    public ApiResponse<PageResponse<PaymentTransaction>> history(
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(payments.myPayments(pageable));
    }
}
