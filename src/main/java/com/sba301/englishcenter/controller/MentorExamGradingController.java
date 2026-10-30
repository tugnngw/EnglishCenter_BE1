package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.GradeAnswerRequest;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import com.sba301.englishcenter.service.ExamGradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mentor/exam-attempts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MENTOR')")
@Tag(name = "Mentor - Exam grading", description = "Manual grading of Writing / Speaking answers")
public class MentorExamGradingController {

    private final ExamGradingService gradingService;

    @GetMapping("/pending")
    @Operation(summary = "Pending grading queue", description = "Attempts with ungraded essay answers, oldest first. "
            + "Mentors see every trial exam plus exams of courses they teach")
    public PageResponse<AttemptSummaryResponse> getPending(@PageableDefault(size = 20) Pageable pageable) {
        return gradingService.getPendingAttempts(pageable);
    }

    @GetMapping("/{attemptId}")
    @Operation(summary = "Get attempt for grading", description = "All answers with correct answers and scores")
    public ApiResponse<AttemptResultResponse> getAttempt(@PathVariable UUID attemptId) {
        return ApiResponse.success(gradingService.getAttemptForGrading(attemptId));
    }

    @PutMapping("/{attemptId}/answers/{questionId}/grade")
    @Operation(summary = "Grade an essay answer", description = "score from 0 to the question's points. "
            + "When nothing is left to grade, a submitted attempt becomes graded")
    public ApiResponse<AttemptResultResponse> grade(@PathVariable UUID attemptId, @PathVariable UUID questionId,
                                                    @Valid @RequestBody GradeAnswerRequest request) {
        return ApiResponse.success("Chấm điểm thành công", gradingService.gradeAnswer(attemptId, questionId, request));
    }
}
