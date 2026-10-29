package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.SaveAnswersRequest;
import com.sba301.englishcenter.dto.request.SubmitAttemptRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import com.sba301.englishcenter.dto.response.ProficiencyResponse;
import com.sba301.englishcenter.service.ExamAttemptService;
import com.sba301.englishcenter.service.ProficiencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'USER')")
@Tag(name = "Student - Exam attempt", description = "Taking an exam, autosave, submit, history and results")
public class ExamAttemptController {

    private final ProficiencyService proficiencyService;
    private final ExamAttemptService attemptService;

    @GetMapping("/attempts/{attemptId}")
    @Operation(summary = "Get attempt", description = "Resume an attempt: questions (no answers), saved answers, "
            + "remainingSeconds. Overdue attempts are auto-submitted")
    public ApiResponse<AttemptDetailResponse> getAttempt(@PathVariable UUID attemptId) {
        return ApiResponse.success(attemptService.getAttempt(attemptId));
    }

    @PutMapping("/attempts/{attemptId}/answers")
    @Operation(summary = "Save answers", description = "Autosave one or many answers while the attempt is in progress")
    public ApiResponse<AttemptDetailResponse> saveAnswers(@PathVariable UUID attemptId,
                                                          @Valid @RequestBody SaveAnswersRequest request) {
        return ApiResponse.success(attemptService.saveAnswers(attemptId, request));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @Operation(summary = "Submit attempt", description = "Locks the attempt and grades objective questions. "
            + "Submitting twice returns 409 (code 5013)")
    public ApiResponse<AttemptResultResponse> submitAttempt(
            @PathVariable UUID attemptId,
            @Valid @RequestBody(required = false) SubmitAttemptRequest request) {
        return ApiResponse.success("Nộp bài thành công", attemptService.submitAttempt(attemptId, request));
    }

    @GetMapping("/me/exam-attempts")
    @Operation(summary = "My attempt history", description = "Newest first; optional examId filter")
    public PageResponse<AttemptSummaryResponse> getMyAttempts(
            @RequestParam(required = false) UUID examId,
            @PageableDefault(size = 20, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return attemptService.getMyAttempts(examId, pageable);
    }

    @GetMapping("/me/proficiency")
    @Operation(summary = "My proficiency", description = "Current level per certificate from the latest submitted "
            + "placement test (trial exam of type placement_test): IELTS band / TOEIC score / VSTEP level. "
            + "status = pending_grading while Writing/Speaking waits for a mentor")
    public ApiResponse<List<ProficiencyResponse>> getMyProficiency(@RequestParam(required = false) UUID certificateId) {
        return ApiResponse.success("Lấy năng lực thành công", proficiencyService.getMyProficiency(certificateId));
    }

    @GetMapping("/me/exam-attempts/{attemptId}/result")
    @Operation(summary = "My attempt result", description = "Total score, section scores, answers and feedback "
            + "(subject to the exam's show_score / show_answers)")
    public ApiResponse<AttemptResultResponse> getResult(@PathVariable UUID attemptId) {
        return ApiResponse.success(attemptService.getMyAttemptResult(attemptId));
    }
}
