package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.CreateHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.request.GradeHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.service.HomeworkSubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/homework-submissions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
@Tag(name = "Student - Homework", description = "Student homework submission operations")
public class HomeworkSubmissionController {

    private final HomeworkSubmissionService submissionService;

    @PostMapping
    @Operation(summary = "Submit homework",
            description = "Submit an answer/file for a homework assignment. Student ID is taken from Security Context.")
    public ApiResponse<HomeworkSubmissionResponse> submitHomework(
            @Valid @RequestBody CreateHomeworkSubmissionRequest request) {
        return ApiResponse.success(submissionService.submitHomework(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Get my submissions",
            description = "Retrieve personal submission history with score and feedback")
    public ApiResponse<List<HomeworkSubmissionResponse>> getMySubmissions() {
        return ApiResponse.success(submissionService.getMySubmissions());
    }
}