package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.request.UploadSignatureRequest;
import com.sba301.englishcenter.dto.response.UploadSignatureResponse;
import com.sba301.englishcenter.service.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cấp chữ ký upload Cloudinary.
 * <p>
 * API secret KHÔNG BAO GIỜ trả về frontend — FE chỉ nhận
 * {@code apiKey + signature + timestamp + cloudName + resourceType + folder}
 * rồi POST thẳng lên Cloudinary.
 */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'USER', 'MENTOR', 'ADMIN')")
@Tag(name = "Upload", description = "Cloudinary upload signatures for homework files")
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/signature")
    @Operation(summary = "Create upload signature",
            description = "Returns a Cloudinary signature. The backend decides resource_type and folder "
                    + "based on the file type and purpose; the API secret never leaves the backend.")
    public ApiResponse<UploadSignatureResponse> createSignature(@Valid @RequestBody UploadSignatureRequest request) {
        return ApiResponse.success(uploadService.createSignature(request));
    }
}
