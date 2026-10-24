package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.ExamDetailResponse;
import com.sba301.englishcenter.dto.response.ExamSummaryResponse;
import com.sba301.englishcenter.service.ExamAttemptService;
import com.sba301.englishcenter.service.ExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exams")
@RequiredArgsConstructor
@Tag(name = "Exam", description = "Public exam catalog, exam outline and starting an attempt")
public class ExamController {

    private final ExamService examService;
    private final ExamAttemptService attemptService;

    @GetMapping
    @Operation(summary = "Get published exams",
            description = "Paginated list of published exams. Filters: keyword, examType, examScope, certificateId, "
                    + "courseId, level (recommendedLevel), difficulty, isFree")
    public PageResponse<ExamSummaryResponse> getExams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) String examScope,
            @RequestParam(required = false) UUID certificateId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Boolean isFree,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return examService.getPublishedExams(pageable, keyword, ExamParams.examType(examType),
                ExamParams.scope(examScope), certificateId, courseId, level, ExamParams.difficulty(difficulty), isFree);
    }

    @GetMapping("/{examId}")
    @Operation(summary = "Get exam detail",
            description = "Admin: full structure with correct answers. Others: section outline + access status "
                    + "(question content is delivered only inside an attempt)")
    public ApiResponse<ExamDetailResponse> getExam(@PathVariable UUID examId) {
        return ApiResponse.success(examService.getExamDetail(examId));
    }

    @PostMapping("/{examId}/attempts")
    @PreAuthorize("hasAnyRole('STUDENT', 'USER')")
    @Operation(summary = "Start an attempt",
            description = "Create an attempt (or resume the in-progress one). Returns questions without answers, "
                    + "expiresAt and remainingSeconds")
    public ApiResponse<AttemptDetailResponse> startAttempt(@PathVariable UUID examId) {
        return ApiResponse.success(attemptService.startAttempt(examId));
    }
}
