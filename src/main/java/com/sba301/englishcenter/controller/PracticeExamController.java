package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.dto.request.SubmitPracticeAnswerRequest;
import com.sba301.englishcenter.dto.response.PracticeAnswerResponse;
import com.sba301.englishcenter.dto.response.PracticeEvaluationResponse;
import com.sba301.englishcenter.dto.response.PracticeExamResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.service.PracticeExamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/practice-exams")
@RequiredArgsConstructor
@Tag(name = "AI Practice", description = "Luyện đề Writing / Speaking tự do, AI (Gemini) chấm theo 4 tiêu chí IELTS")
public class PracticeExamController {

    private final PracticeExamService practiceExamService;

    @GetMapping
    @Operation(summary = "Danh sách đề luyện tập (public)", description = "Lọc theo type WRITING / SPEAKING, keyword (tiêu đề, đề bài) và certificateId")
    public PageResponse<PracticeExamResponse> getPracticeExams(
            @RequestParam(required = false) PracticeExamType type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID certificateId,
            @PageableDefault(size = 10) Pageable pageable) {
        return practiceExamService.getPracticeExams(type, keyword, certificateId, pageable);
    }

    @GetMapping("/{practiceExamId}")
    @Operation(summary = "Chi tiết đề luyện tập (public)")
    public ApiResponse<PracticeExamResponse> getPracticeExam(@PathVariable UUID practiceExamId) {
        return ApiResponse.success(practiceExamService.getPracticeExam(practiceExamId));
    }

    @PostMapping("/{practiceExamId}/submit")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Nộp bài làm", description = "Được nộp nhiều lần; mỗi lần là một bài làm mới trạng thái SUBMITTED")
    public ApiResponse<PracticeAnswerResponse> submit(@PathVariable UUID practiceExamId,
                                                      @Valid @RequestBody SubmitPracticeAnswerRequest request) {
        return ApiResponse.success(practiceExamService.submit(currentUserId(), practiceExamId, request));
    }

    @PostMapping("/answers/{answerId}/ai-evaluate")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Gọi AI chấm bài",
            description = "Mất 10 - 60 giây. Bài đã chấm thì trả lại kết quả cũ. 6027: chưa cấu hình GEMINI_API_KEY; 6028: AI lỗi, thử lại sau")
    public ApiResponse<PracticeEvaluationResponse> evaluate(@PathVariable UUID answerId) {
        return ApiResponse.success(practiceExamService.evaluate(currentUserId(), answerId));
    }

    @GetMapping("/evaluations/{evaluationId}")
    @Operation(summary = "Xem kết quả AI chấm", description = "Chỉ chủ bài làm hoặc ADMIN")
    public ApiResponse<PracticeEvaluationResponse> getEvaluation(@PathVariable UUID evaluationId) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return ApiResponse.success(practiceExamService.getEvaluation(principal.getUserId(), isAdmin, evaluationId));
    }

    @GetMapping("/me/answers")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Lịch sử bài làm của tôi", description = "Mới nhất trước; có evaluationId / bandScore nếu đã chấm")
    public ApiResponse<List<PracticeAnswerResponse>> getMyAnswers() {
        return ApiResponse.success(practiceExamService.getMyAnswers(currentUserId()));
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
