package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.CreateCertificateRequest;
import com.sba301.englishcenter.dto.request.UpdateCertificateRequest;
import com.sba301.englishcenter.dto.response.CertificateResponse;

import java.util.List;
import java.util.UUID;

public interface CertificateService {

    List<CertificateResponse> getAllCertificates();

    CertificateResponse getCertificateById(UUID certificateId);

    CertificateResponse createCertificate(CreateCertificateRequest request);

    CertificateResponse updateCertificate(UUID certificateId, UpdateCertificateRequest request);

    void deleteCertificate(UUID certificateId);
}