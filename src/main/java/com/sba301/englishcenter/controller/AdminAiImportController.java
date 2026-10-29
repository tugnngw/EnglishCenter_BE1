package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.AiImportRequest;
import com.sba301.englishcenter.dto.response.AiImportResponse;
import com.sba301.englishcenter.service.ExamAiImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Exam", description = "Exam, section, question and answer management")
public class AdminAiImportController {

    private final ExamAiImportService aiImportService;

    @PostMapping("/ai-import")
    @Operation(summary = "Read a test file with AI",
            description = "Send a PDF (fileBase64) or the text of a Word / text file. target=EXAM returns the exam in the "
                    + "import format ([SECTION], questions, *answers) for the client to preview and create; "
                    + "target=PRACTICE returns Writing / Speaking practice prompts. Nothing is saved.")
    public ApiResponse<AiImportResponse> parse(@Valid @RequestBody AiImportRequest request) {
        return ApiResponse.success("AI đã đọc file đề", aiImportService.parse(request));
    }
}
