package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.CreateMentorRequest;
import com.sba301.englishcenter.dto.request.UpdateMentorRequest;
import com.sba301.englishcenter.dto.response.CreateMentorResponse;
import com.sba301.englishcenter.dto.response.MentorResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface MentorService {

    // ============================================
    // ADMIN ENDPOINTS
    // ============================================

    /**
     * Create a new mentor account.
     * Generates temporary password and sets mustChangePassword flag.
     * Does NOT send email - returns temp password in response.
     *
     * @param request Mentor creation request
     * @return CreateMentorResponse with temp password
     */
    CreateMentorResponse createMentor(CreateMentorRequest request);

    /**
     * Update mentor profile.
     *
     * @param mentorId Mentor user ID
     * @param request Update request
     * @return Updated mentor profile
     */
    MentorResponse updateMentor(UUID mentorId, UpdateMentorRequest request);

    /**
     * Revoke mentor status - change role to STUDENT.
     *
     * @param mentorId Mentor user ID
     */
    void revokeMentor(UUID mentorId);

    // ============================================
    // PUBLIC ENDPOINTS
    // ============================================

    /**
     * Get paginated list of mentors with optional filters.
     *
     * @param pageable Pagination parameters
     * @param specialization Filter by specialization
     * @param minRate Minimum hourly rate
     * @param maxRate Maximum hourly rate
     * @return Paginated mentor list
     */
    PageResponse<MentorResponse> getMentors(Pageable pageable, String specialization,
                                             BigDecimal minRate, BigDecimal maxRate);

    /**
     * Get mentor profile by ID.
     *
     * @param mentorId Mentor user ID
     * @return Mentor profile details
     */
    MentorResponse getMentorById(UUID mentorId);
}
