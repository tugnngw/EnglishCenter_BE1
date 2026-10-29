package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.GradeHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.response.HomeworkGradeHistoryResponse;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.service.HomeworkGradingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Mentor/Admin chấm bài tập (tự luận + nộp file).
 * <p>
 * Bài trắc nghiệm được chấm tự động lúc học viên nộp → không có trong đây.
 */
@RestController
@RequestMapping("/api/v1/mentor/homework-submissions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MENTOR')")
@Tag(name = "Mentor - Homework grading", description = "Grade homework submissions (written / file)")
public class MentorHomeworkGradingController {

    private final HomeworkGradingService gradingService;

    @PutMapping("/{submissionId}/grade")
    @Operation(summary = "Grade a homework submission",
            description = "Sets score + feedback. `passed` is recomputed from the homework's passing score. "
                    + "Every score change is recorded in the grade history.")
    public ApiResponse<HomeworkSubmissionResponse> grade(
            @PathVariable UUID submissionId,
            @Valid @RequestBody GradeHomeworkSubmissionRequest request) {
        return ApiResponse.success("Chấm điểm thành công", gradingService.grade(submissionId, request));
    }

    @GetMapping("/{submissionId}/history")
    @Operation(summary = "Grade history",
            description = "History of score changes: who graded, old score, new score, time")
    public ApiResponse<List<HomeworkGradeHistoryResponse>> getHistory(@PathVariable UUID submissionId) {
        return ApiResponse.success(gradingService.getHistory(submissionId));
    }
}
