package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.AnswerItemRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.service.HomeworkExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Làm bài TRẮC NGHIỆM cho bài tập (Dev 2.5).
 * <p>
 * Học viên làm bài ngay trong bài tập, không phải rời sang phòng thi của module Exam.
 * Luồng:
 * <ol>
 *     <li>{@code POST /api/v1/homework/{homeworkContentId}/attempt} — mở lượt thi;</li>
 *     <li>{@code POST /api/v1/homework/attempts/{attemptId}/submit} — nộp bài, BE chấm tự động
 *         và tạo luôn bài nộp {@code homework_submissions}.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/homework")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
@Tag(name = "Homework - Multiple choice", description = "Do a multiple-choice homework attempt")
public class HomeworkExamController {

    private final HomeworkExamService homeworkExamService;

    @PostMapping("/{homeworkContentId}/attempt")
    @Operation(summary = "Start a homework attempt",
            description = "Opens (or resumes) the exam attempt linked to a multiple-choice homework.")
    public ApiResponse<AttemptDetailResponse> startAttempt(@PathVariable UUID homeworkContentId) {
        return ApiResponse.success("Mở phòng làm bài thành công",
                homeworkExamService.startAttempt(homeworkContentId));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @Operation(summary = "Submit a homework attempt",
            description = "Submits the attempt, auto-grades it and creates the homework submission.")
    public ApiResponse<AttemptResultResponse> submitAttempt(
            @PathVariable UUID attemptId,
            @Valid @RequestBody(required = false) List<AnswerItemRequest> answers) {
        return ApiResponse.success("Nộp bài thành công",
                homeworkExamService.submitAttempt(attemptId, answers));
    }

    @GetMapping("/attempts/{attemptId}")
    @Operation(summary = "Get a homework attempt",
            description = "Returns the attempt with its questions (for resuming an attempt).")
    public ApiResponse<AttemptDetailResponse> getAttempt(@PathVariable UUID attemptId) {
        return ApiResponse.success(homeworkExamService.getAttempt(attemptId));
    }
}
