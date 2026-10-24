package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.CreateExamQuestionRequest;
import com.sba301.englishcenter.dto.request.CreateExamRequest;
import com.sba301.englishcenter.dto.request.CreateExamSectionRequest;
import com.sba301.englishcenter.dto.request.UpdateExamQuestionRequest;
import com.sba301.englishcenter.dto.request.UpdateExamRequest;
import com.sba301.englishcenter.dto.request.UpdateExamSectionRequest;
import com.sba301.englishcenter.dto.response.ExamDetailResponse;
import com.sba301.englishcenter.dto.response.ExamQuestionResponse;
import com.sba301.englishcenter.dto.response.ExamSectionResponse;
import com.sba301.englishcenter.dto.response.ExamSummaryResponse;
import com.sba301.englishcenter.service.ExamService;
import com.sba301.englishcenter.service.ExamStructureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Exam", description = "Exam, section, question and answer management")
public class AdminExamController {

    private final ExamService examService;
    private final ExamStructureService structureService;

    // ============================================
    // EXAM (D3-02, D3-06)
    // ============================================

    @GetMapping("/exams")
    @Operation(summary = "List exams (admin)", description = "All statuses; filter by status, examType, examScope, "
            + "certificateId, courseId, level, difficulty, isFree, keyword")
    public PageResponse<ExamSummaryResponse> getExams(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String examScope,
            @RequestParam(required = false) UUID certificateId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Boolean isFree,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return examService.getExamsForAdmin(pageable, keyword, ExamParams.examType(examType),
                ExamParams.status(status), ExamParams.scope(examScope), certificateId, courseId, level,
                ExamParams.difficulty(difficulty), isFree);
    }

    @PostMapping("/exams")
    @Operation(summary = "Create exam", description = "New exam is always DRAFT. trial requires certificateId, "
            + "course requires courseId")
    public ApiResponse<ExamDetailResponse> createExam(@Valid @RequestBody CreateExamRequest request) {
        return ApiResponse.success("Tạo đề thi thành công", examService.createExam(request));
    }

    @GetMapping("/exams/{examId}")
    @Operation(summary = "Get exam with answers", description = "Full structure including correct answers")
    public ApiResponse<ExamDetailResponse> getExam(@PathVariable UUID examId) {
        return ApiResponse.success(examService.getExamDetail(examId));
    }

    @PutMapping("/exams/{examId}")
    @Operation(summary = "Update exam", description = "Null fields are kept unchanged")
    public ApiResponse<ExamDetailResponse> updateExam(@PathVariable UUID examId,
                                                      @Valid @RequestBody UpdateExamRequest request) {
        return ApiResponse.success("Cập nhật đề thi thành công", examService.updateExam(examId, request));
    }

    @DeleteMapping("/exams/{examId}")
    @Operation(summary = "Delete exam", description = "Rejected when the exam already has attempts (use unpublish)")
    public ApiResponse<Void> deleteExam(@PathVariable UUID examId) {
        examService.deleteExam(examId);
        return ApiResponse.success("Xóa đề thi thành công", null);
    }

    @PostMapping("/exams/{examId}/publish")
    @Operation(summary = "Publish exam", description = "Validates that every question has valid answers")
    public ApiResponse<ExamDetailResponse> publishExam(@PathVariable UUID examId) {
        return ApiResponse.success("Đã công khai đề thi", examService.publishExam(examId));
    }

    @PostMapping("/exams/{examId}/unpublish")
    @Operation(summary = "Unpublish exam", description = "Back to DRAFT; running attempts can still finish")
    public ApiResponse<ExamDetailResponse> unpublishExam(@PathVariable UUID examId) {
        return ApiResponse.success("Đã ẩn đề thi", examService.unpublishExam(examId));
    }

    // ============================================
    // SECTIONS (D3-03)
    // ============================================

    @PostMapping("/exams/{examId}/sections")
    @Operation(summary = "Add section", description = "sectionType: LISTENING / READING / WRITING / SPEAKING / "
            + "GENERAL or any custom value")
    public ApiResponse<ExamSectionResponse> createSection(@PathVariable UUID examId,
                                                          @Valid @RequestBody CreateExamSectionRequest request) {
        return ApiResponse.success(structureService.createSection(examId, request));
    }

    @PutMapping("/exams/{examId}/sections/{sectionId}")
    @Operation(summary = "Update section")
    public ApiResponse<ExamSectionResponse> updateSection(@PathVariable UUID examId, @PathVariable UUID sectionId,
                                                          @Valid @RequestBody UpdateExamSectionRequest request) {
        return ApiResponse.success(structureService.updateSection(examId, sectionId, request));
    }

    @DeleteMapping("/exams/{examId}/sections/{sectionId}")
    @Operation(summary = "Delete section", description = "Also removes the section's questions from the exam")
    public ApiResponse<Void> deleteSection(@PathVariable UUID examId, @PathVariable UUID sectionId) {
        structureService.deleteSection(examId, sectionId);
        return ApiResponse.success("Xóa section thành công", null);
    }

    // ============================================
    // QUESTIONS & OPTIONS (D3-04, D3-05)
    // ============================================

    @PostMapping("/exams/{examId}/questions")
    @Operation(summary = "Add question", description = "Either reuse a bank question (questionId) or create a new "
            + "one with questionType, questionText and options")
    public ApiResponse<ExamQuestionResponse> addQuestion(@PathVariable UUID examId,
                                                         @Valid @RequestBody CreateExamQuestionRequest request) {
        return ApiResponse.success(structureService.addQuestion(examId, request));
    }

    @PutMapping("/exams/{examId}/questions/{questionId}")
    @Operation(summary = "Update question", description = "options != null replaces all options")
    public ApiResponse<ExamQuestionResponse> updateQuestion(@PathVariable UUID examId, @PathVariable UUID questionId,
                                                            @Valid @RequestBody UpdateExamQuestionRequest request) {
        return ApiResponse.success(structureService.updateQuestion(examId, questionId, request));
    }

    @DeleteMapping("/exams/{examId}/questions/{questionId}")
    @Operation(summary = "Remove question from exam", description = "The question stays in the question bank")
    public ApiResponse<Void> removeQuestion(@PathVariable UUID examId, @PathVariable UUID questionId) {
        structureService.removeQuestion(examId, questionId);
        return ApiResponse.success("Đã gỡ câu hỏi khỏi đề", null);
    }

    // ============================================
    // QUESTION BANK
    // ============================================

    @GetMapping("/questions")
    @Operation(summary = "Search question bank")
    public PageResponse<ExamQuestionResponse> searchQuestions(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) UUID certificateId,
            @RequestParam(required = false) UUID certificatePartId,
            @RequestParam(required = false) String topic,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return structureService.searchQuestionBank(pageable, keyword, ExamParams.questionType(questionType),
                ExamParams.difficulty(difficulty), certificateId, certificatePartId, topic);
    }

    @GetMapping("/questions/{questionId}")
    @Operation(summary = "Get bank question")
    public ApiResponse<ExamQuestionResponse> getQuestion(@PathVariable UUID questionId) {
        return ApiResponse.success(structureService.getBankQuestion(questionId));
    }
}
