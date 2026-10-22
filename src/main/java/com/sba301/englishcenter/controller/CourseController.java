package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.CourseContentResponse;
import com.sba301.englishcenter.dto.response.CourseResponse;
import com.sba301.englishcenter.service.CourseService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "Course", description = "Public course catalog, detail, and outline operations")
public class CourseController {

    private final CourseService courseService;

    // ============================================
    // CATALOG & DETAIL
    // ============================================

    @GetMapping
    @Operation(summary = "Get courses with filters",
            description = "Retrieve paginated list of courses with filters (keyword, certificate, level, targetScore, status)")
    public PageResponse<CourseResponse> getCourses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID certificateId,
            @RequestParam(required = false) UUID certificatePartId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) BigDecimal targetScore,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return courseService.getCourses(pageable, keyword, certificateId, certificatePartId,
                level, targetScore, status);
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Get course by ID",
            description = "Retrieve detailed information of a specific course")
    public ApiResponse<CourseResponse> getCourseById(@PathVariable UUID courseId) {
        return ApiResponse.success(courseService.getCourseById(courseId));
    }

    // ============================================
    // OUTLINE / CONTENTS  (Dev 2)
    // ============================================

    @GetMapping("/{courseId}/contents")
    @Operation(summary = "Get course outline",
            description = "Retrieve full outline with preview/locked markers applied")
    public ApiResponse<List<CourseContentResponse>> getCourseContents(@PathVariable UUID courseId) {
        return ApiResponse.success(courseService.getCourseContents(courseId));
    }

    @GetMapping("/{courseId}/vocabularies")
    @Operation(summary = "Get course vocabularies",
            description = "Retrieve vocabulary flashcards for a course")
    public ApiResponse<List<CourseContentResponse>> getVocabularies(@PathVariable UUID courseId) {
        return ApiResponse.success(courseService.getVocabularies(courseId));
    }

    @GetMapping("/{courseId}/homework")
    @Operation(summary = "Get course homework list",
            description = "Retrieve the list of homework assignments for a course")
    public ApiResponse<List<CourseContentResponse>> getHomeworkList(@PathVariable UUID courseId) {
        return ApiResponse.success(courseService.getHomeworkList(courseId));
    }
}
