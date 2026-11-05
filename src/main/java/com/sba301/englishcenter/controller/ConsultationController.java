package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.CreateConsultationRequest;
import com.sba301.englishcenter.dto.request.UpdateConsultationStatusRequest;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.AttemptSummaryResponse;
import com.sba301.englishcenter.dto.response.ConsultationResponse;
import com.sba301.englishcenter.dto.response.StudentOverviewResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.ConsultationService;
import com.sba301.englishcenter.service.ExamAttemptService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Consultation", description = "Tư vấn lộ trình: học viên để lại SĐT, mentor xem điểm test level và tư vấn")
public class ConsultationController {

    private final ConsultationService consultationService;
    private final ExamAttemptService examAttemptService;

    // ===================== HỌC VIÊN =====================

    @PostMapping("/me/consultation-requests")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Để lại SĐT nhờ mentor tư vấn lộ trình", description = "Đang có yêu cầu mở thì cập nhật SĐT / ghi chú")
    public ApiResponse<ConsultationResponse> request(@Valid @RequestBody CreateConsultationRequest request) {
        return ApiResponse.success(consultationService.request(currentUserId(), request));
    }

    @GetMapping("/me/consultation-requests/latest")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Yêu cầu tư vấn gần nhất của tôi", description = "data = null nếu chưa gửi")
    public ApiResponse<ConsultationResponse> getMyLatest() {
        return ApiResponse.success(consultationService.getMyLatest(currentUserId()));
    }

    // ===================== MENTOR =====================

    @GetMapping("/mentor/consultation-requests")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Yêu cầu tư vấn", description = "mine = false: chưa ai nhận (mentor nào cũng thấy); mine = true: tôi phụ trách")
    public PageResponse<ConsultationResponse> getRequests(@RequestParam(defaultValue = "false") boolean mine,
                                                          @PageableDefault(size = 10) Pageable pageable) {
        return consultationService.getForMentor(currentUserId(), mine, pageable);
    }

    @PutMapping("/mentor/consultation-requests/{requestId}/claim")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Nhận tư vấn yêu cầu", description = "Mentor nhận trước phụ trách, mentor khác không nhận được nữa")
    public ApiResponse<ConsultationResponse> claim(@PathVariable UUID requestId) {
        return ApiResponse.success(consultationService.claim(currentUserId(), requestId));
    }

    @PutMapping("/mentor/consultation-requests/{requestId}/status")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Cập nhật yêu cầu mình phụ trách", description = "status = contacted hoặc closed")
    public ApiResponse<ConsultationResponse> updateStatus(@PathVariable UUID requestId,
                                                          @Valid @RequestBody UpdateConsultationStatusRequest request) {
        return ApiResponse.success(consultationService.updateStatus(currentUserId(), requestId, request.getStatus()));
    }

    @GetMapping("/mentor/students")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Tìm học viên", description = "Theo tên / email / SĐT; kèm nguyện vọng và trình độ test level")
    public PageResponse<StudentOverviewResponse> searchStudents(@RequestParam(required = false) String keyword,
                                                                @PageableDefault(size = 10) Pageable pageable) {
        return consultationService.searchStudents(keyword, pageable);
    }

    @GetMapping("/mentor/students/{studentId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Hồ sơ tư vấn của học viên")
    public ApiResponse<StudentOverviewResponse> getStudent(@PathVariable UUID studentId) {
        return ApiResponse.success(consultationService.getStudent(studentId));
    }

    @GetMapping("/mentor/students/{studentId}/level-tests")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Các lượt test level của học viên")
    public PageResponse<AttemptSummaryResponse> getLevelTests(@PathVariable UUID studentId,
                                                              @PageableDefault(size = 10) Pageable pageable) {
        consultationService.getStudent(studentId);
        return examAttemptService.getStudentLevelTests(studentId, pageable);
    }

    @GetMapping("/mentor/students/{studentId}/level-tests/{attemptId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Kết quả chi tiết 1 lượt test level", description = "Điểm từng kỹ năng, tóm tắt, nhận xét AI")
    public ApiResponse<AttemptResultResponse> getLevelTestResult(@PathVariable UUID studentId, @PathVariable UUID attemptId) {
        return ApiResponse.success(examAttemptService.getStudentLevelTestResult(studentId, attemptId));
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
