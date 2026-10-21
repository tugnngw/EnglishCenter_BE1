package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.MentorResponse;
import com.sba301.englishcenter.service.MentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mentors")
@RequiredArgsConstructor
@Tag(name = "Mentor", description = "Public mentor directory and profile operations")
public class MentorController {

    private final MentorService mentorService;

    @GetMapping
    @Operation(summary = "Get all mentors with filters",
            description = "Retrieve paginated list of active mentors with optional specialization and rate filters")
    public PageResponse<MentorResponse> getMentors(
            @RequestParam(required = false) String specialization,
            @RequestParam(required = false) BigDecimal minRate,
            @RequestParam(required = false) BigDecimal maxRate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return mentorService.getMentors(pageable, specialization, minRate, maxRate);
    }

    @GetMapping("/{mentorId}")
    @Operation(summary = "Get mentor by ID", description = "Retrieve detailed profile of a specific mentor")
    public ApiResponse<MentorResponse> getMentorById(@PathVariable UUID mentorId) {
        return ApiResponse.success(mentorService.getMentorById(mentorId));
    }
}
