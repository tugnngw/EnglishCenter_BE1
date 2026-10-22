package com.sba301.englishcenter.controller;

import com.sba301.englishcenter.common.ApiResponse;
import com.sba301.englishcenter.dto.response.CertificateResponse;
import com.sba301.englishcenter.service.CertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/certificates")
@RequiredArgsConstructor
@Tag(name = "Certificate", description = "Public certificate catalog operations")
public class CertificateController {

    private final CertificateService certificateService;

    @GetMapping
    @Operation(summary = "Get all certificates",
            description = "Retrieve certificate catalog (sorted by name)")
    public ApiResponse<List<CertificateResponse>> getAllCertificates() {
        return ApiResponse.success(certificateService.getAllCertificates());
    }

    @GetMapping("/{certificateId}")
    @Operation(summary = "Get certificate by ID",
            description = "Retrieve detail of a certificate and its parts")
    public ApiResponse<CertificateResponse> getCertificateById(@PathVariable UUID certificateId) {
        return ApiResponse.success(certificateService.getCertificateById(certificateId));
    }
}