package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.HomeworkAiImportRequest;
import com.sba301.englishcenter.dto.request.MentorHomeworkRequest;
import com.sba301.englishcenter.dto.response.CourseContentResponse;
import com.sba301.englishcenter.dto.response.HomeworkAiDraftResponse;
import com.sba301.englishcenter.dto.response.MentorHomeworkResponse;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.MentorHomeworkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Bài tập riêng của mentor (Dev 5, V27) - trang "Homework" của mentor.
 * Bài nộp / chấm dùng lại API homework của Dev 2: POST /api/v1/homework-submissions (học viên nộp),
 * GET /api/v1/mentor/homework-submissions (mentor chấm).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Mentor Homework", description = "Mentor soạn bài tập (import PDF), gắn vào lộ trình; học viên làm từ lộ trình")
public class MentorHomeworkController {

    private final MentorHomeworkService mentorHomeworkService;

    // ===================== MENTOR =====================

    @GetMapping("/mentor/homeworks")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Bài tập của tôi", description = "Kèm lộ trình đang chứa bài và số bài nộp")
    public ApiResponse<List<MentorHomeworkResponse>> getMine() {
        return ApiResponse.success(mentorHomeworkService.getMine(currentUserId()));
    }

    @PostMapping("/mentor/homeworks/ai-import")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "AI đọc PDF đề bài tập", description = "PDF base64 (tối đa 15MB) -> bản nháp, chưa lưu")
    public ApiResponse<HomeworkAiDraftResponse> importFromPdf(@Valid @RequestBody HomeworkAiImportRequest request) {
        return ApiResponse.success(mentorHomeworkService.importFromPdf(request));
    }

    @PostMapping("/mentor/homeworks")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Tạo bài tập")
    public ApiResponse<MentorHomeworkResponse> create(@Valid @RequestBody MentorHomeworkRequest request) {
        return ApiResponse.success(mentorHomeworkService.create(currentUserId(), request));
    }

    @PutMapping("/mentor/homeworks/{contentId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Sửa bài tập", description = "Đã có bài nộp thì không đổi hình thức nộp / điểm tối đa")
    public ApiResponse<MentorHomeworkResponse> update(@PathVariable UUID contentId,
                                                      @Valid @RequestBody MentorHomeworkRequest request) {
        return ApiResponse.success(mentorHomeworkService.update(currentUserId(), contentId, request));
    }

    @DeleteMapping("/mentor/homeworks/{contentId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Xóa bài tập", description = "Chỉ khi chưa có bài nộp và không nằm trong lộ trình nào")
    public ApiResponse<Void> delete(@PathVariable UUID contentId) {
        mentorHomeworkService.delete(currentUserId(), contentId);
        return ApiResponse.success();
    }

    // ===================== HỌC VIÊN =====================

    @GetMapping("/me/homeworks/{contentId}")
    @PreAuthorize("hasAnyRole('USER', 'STUDENT')")
    @Operation(summary = "Mở bài tập từ lộ trình",
            description = "Phải mua gói kèm 1-1 và ghi danh lộ trình chứa bài; contentData.dueDate = ngày ghi danh + dueDays")
    public ApiResponse<CourseContentResponse> getForStudent(@PathVariable UUID contentId) {
        return ApiResponse.success(mentorHomeworkService.getForStudent(currentUserId(), contentId));
    }

    private UUID currentUserId() {
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
    }
}
