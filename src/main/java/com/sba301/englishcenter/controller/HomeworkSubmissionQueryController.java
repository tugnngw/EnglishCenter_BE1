package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.service.HomeworkQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Truy vấn bài nộp bài tập.
 * <ul>
 *     <li>{@code /api/v1/homework-submissions/me} — học viên xem bài nộp của mình;</li>
 *     <li>{@code /api/v1/homework-submissions/homework/{id}/mine} — bài nộp của mình cho 1 bài tập;</li>
 *     <li>{@code /api/v1/mentor/homework-submissions} — hàng đợi chấm của mentor.</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Homework submissions", description = "Query homework submissions")
public class HomeworkSubmissionQueryController {

    private final HomeworkQueryService queryService;

    /* ============================================
     * STUDENT
     * ============================================ */

    @GetMapping("/api/v1/homework-submissions/homework/{homeworkContentId}/mine")
    @PreAuthorize("hasAnyRole('STUDENT', 'USER')")
    @Operation(summary = "My submission for a homework",
            description = "Submissions of the current student for one homework "
                    + "(empty list = not submitted yet)")
    public ApiResponse<List<HomeworkSubmissionResponse>> getMySubmissionForHomework(
            @PathVariable UUID homeworkContentId) {
        return ApiResponse.success(queryService.getMySubmissionsForHomework(homeworkContentId));
    }

    /* ============================================
     * MENTOR
     * ============================================ */

    @GetMapping("/api/v1/mentor/homework-submissions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR')")
    @Operation(summary = "Grading queue",
            description = "Homework submissions of the courses this mentor teaches. "
                    + "pendingOnly=true → only ungraded submissions")
    public PageResponse<HomeworkSubmissionResponse> getQueue(
            @RequestParam(defaultValue = "true") boolean pendingOnly,
            @PageableDefault(size = 20, sort = "submittedDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryService.getMentorQueue(pageable, pendingOnly);
    }

    @GetMapping("/api/v1/mentor/homework-submissions/{submissionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MENTOR')")
    @Operation(summary = "Submission detail for grading",
            description = "Full submission incl. grade history. Mentors only see their own courses.")
    public ApiResponse<HomeworkSubmissionResponse> getForGrading(@PathVariable UUID submissionId) {
        return ApiResponse.success(queryService.getForGrading(submissionId));
    }
}
