package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.AssignVocabularySetsRequest;
import com.sba301.englishcenter.dto.request.CreateVocabularySetRequest;
import com.sba301.englishcenter.dto.response.CourseVocabularySetResponse;
import com.sba301.englishcenter.dto.response.VocabularySetResponse;
import com.sba301.englishcenter.service.VocabularyService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * API cho KHO TỪ VỰNG CHUNG + GÁN VÀO KHÓA HỌC.
 * <p>
 * <b>Luồng 1 — Tạo từ vựng chung</b> (không gắn khóa học):
 * <pre>
 *   GET    /api/v1/vocabulary-sets            → duyệt kho chung
 *   POST   /api/v1/vocabulary-sets            → tạo bộ mới trong kho chung
 *   PUT    /api/v1/vocabulary-sets/{setId}    → sửa bộ trong kho chung
 *   DELETE /api/v1/vocabulary-sets/{setId}    → xoá bộ khỏi kho chung
 * </pre>
 * <b>Luồng 2 — Gán vào khóa học</b>:
 * <pre>
 *   GET    /api/v1/courses/{courseId}/vocabularies         → các bộ đã gán cho khóa học
 *   PUT    /api/v1/vocabulary-sets/courses/{courseId}       → tick chọn &amp; lưu
 *   DELETE /api/v1/vocabulary-sets/courses/{courseId}/{setId} → gỡ 1 bộ khỏi khóa học
 * </pre>
 * Endpoint gán nằm ở controller này (không phải {@code /api/v1/admin/**}) để
 * MENTOR cũng dùng được — {@code AdminController} bị {@code SecurityConfig}
 * giới hạn cho {@code hasRole("ADMIN")}.
 */
@RestController
@RequestMapping("/api/v1/vocabulary-sets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MENTOR')")
@Tag(name = "Vocabulary Bank", description = "Centralized vocabulary bank - create globally, assign to courses")
public class VocabularyController {

    private final VocabularyService vocabularyService;

    // ============================================
    // KHO TỪ VỰNG CHUNG
    // ============================================

    @GetMapping
    @Operation(summary = "Get vocabulary bank",
            description = "List every vocabulary set in the global bank. Sets are independent from any course.")
    public ApiResponse<List<VocabularySetResponse>> getBank(
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(vocabularyService.getBankSets(keyword));
    }

    @GetMapping("/{setId}")
    @Operation(summary = "Get vocabulary set detail",
            description = "Retrieve a single vocabulary set from the global bank with its words")
    public ApiResponse<VocabularySetResponse> getBankSet(@PathVariable UUID setId) {
        return ApiResponse.success(vocabularyService.getBankSet(setId));
    }

    @PostMapping
    @Operation(summary = "Create vocabulary set",
            description = "Create a vocabulary set in the global bank. No course is required - words already "
                    + "existing in the bank are reused instead of duplicated.")
    public ApiResponse<VocabularySetResponse> createBankSet(
            @Valid @RequestBody CreateVocabularySetRequest request) {
        return ApiResponse.success(vocabularyService.createBankSet(request));
    }

    @PutMapping("/{setId}")
    @Operation(summary = "Update vocabulary set",
            description = "Update title, description and the word list of a set in the global bank")
    public ApiResponse<VocabularySetResponse> updateBankSet(
            @PathVariable UUID setId,
            @Valid @RequestBody CreateVocabularySetRequest request) {
        return ApiResponse.success(vocabularyService.updateBankSet(setId, request));
    }

    @DeleteMapping("/{setId}")
    @Operation(summary = "Delete vocabulary set",
            description = "Delete a set from the global bank. The set is also unassigned from every course using it.")
    public ApiResponse<Void> deleteBankSet(@PathVariable UUID setId) {
        vocabularyService.deleteBankSet(setId);
        return ApiResponse.success();
    }

    // ============================================
    // GÁN VÀO KHÓA HỌC
    // ============================================

    @PutMapping("/courses/{courseId}")
    @Operation(summary = "Assign vocabulary sets to course",
            description = "Replace the list of vocabulary sets assigned to a course. Sets in the request are "
                    + "assigned; sets missing from the request are unassigned.")
    public ApiResponse<List<CourseVocabularySetResponse>> assignToCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody AssignVocabularySetsRequest request) {
        return ApiResponse.success(vocabularyService.assignSets(courseId, request));
    }

    @DeleteMapping("/courses/{courseId}/{setId}")
    @Operation(summary = "Unassign vocabulary set from course",
            description = "Remove a set from a course without deleting it from the global bank")
    public ApiResponse<Void> unassignFromCourse(
            @PathVariable UUID courseId,
            @PathVariable UUID setId) {
        vocabularyService.unassignSet(courseId, setId);
        return ApiResponse.success();
    }
}
