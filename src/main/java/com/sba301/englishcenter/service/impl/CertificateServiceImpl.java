package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.dto.request.CreateCertificateRequest;
import com.sba301.englishcenter.dto.request.UpdateCertificateRequest;
import com.sba301.englishcenter.dto.response.CertificatePartResponse;
import com.sba301.englishcenter.dto.response.CertificateResponse;
import com.sba301.englishcenter.entity.Certificate;
import com.sba301.englishcenter.entity.CertificatePart;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CertificatePartRepository;
import com.sba301.englishcenter.repository.CertificateRepository;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.CertificateService;
import com.sba301.englishcenter.util.CertificateScoringValidator;
import com.sba301.englishcenter.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateServiceImpl implements CertificateService {

    private final CertificateRepository certificateRepository;
    private final CertificatePartRepository certificatePartRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CertificateResponse> getAllCertificates() {
        List<Certificate> certificates = certificateRepository.findAllByOrderByCertificateNameAsc();
        return certificates.stream()
                .map(this::buildCertificateSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CertificateResponse getCertificateById(UUID certificateId) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));

        // Nạp parts theo thứ tự, tránh N+1 khi trả về chi tiết
        List<CertificatePart> parts =
                certificatePartRepository.findByCertificate_CertificateIdOrderByPartOrderAsc(certificateId);

        return buildCertificateDetail(certificate, parts);
    }

    @Override
    @Transactional
    public CertificateResponse createCertificate(CreateCertificateRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        User creator = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Certificate certificate = Certificate.builder()
                .certificateName(request.getCertificateName())
                .description(request.getDescription())
                .createdByUser(creator)
                .build();

        // Kiểm tra cấu hình thang điểm trước khi ghi DB (bảo vệ dữ liệu ở server)
        CertificateScoringValidator.validate(request.getDescription(), request.getParts());

        certificate = certificateRepository.save(certificate);

        // Tạo kèm các phần của chứng chỉ
        if (request.getParts() != null && !request.getParts().isEmpty()) {
            List<CertificatePart> parts = new ArrayList<>();
            int order = 1;
            for (CreateCertificateRequest.PartRequest partRequest : request.getParts()) {
                parts.add(CertificatePart.builder()
                        .certificate(certificate)
                        .partName(partRequest.getPartName())
                        .partDescription(partRequest.getPartDescription())
                        .partOrder(order++)
                        .properties(normalizeProperties(partRequest.getProperties()))
                        .build());
            }
            certificatePartRepository.saveAll(parts);
            log.info("Đã tạo chứng chỉ: certificateId={}, số phần={}", certificate.getCertificateId(), parts.size());
        } else {
            log.info("Đã tạo chứng chỉ: certificateId={}", certificate.getCertificateId());
        }

        List<CertificatePart> savedParts =
                certificatePartRepository.findByCertificate_CertificateIdOrderByPartOrderAsc(certificate.getCertificateId());

        return buildCertificateDetail(certificate, savedParts);
    }

    @Override
    @Transactional
    public CertificateResponse updateCertificate(UUID certificateId, UpdateCertificateRequest request) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));

        if (request.getCertificateName() != null && !request.getCertificateName().isBlank()) {
            certificate.setCertificateName(request.getCertificateName());
        }
        if (request.getDescription() != null) {
            certificate.setDescription(request.getDescription());
        }
        certificate.setLastUpdated(LocalDateTime.now());

        // Kiểm tra cấu hình thang điểm trước khi ghi DB (bảo vệ dữ liệu ở server)
        CertificateScoringValidator.validate(certificate.getDescription(), request.getParts());

        certificate = certificateRepository.save(certificate);

        // Cập nhật hoặc bổ sung các kỹ năng/phần nếu có gửi kèm
        if (request.getParts() != null) {
            List<CertificatePart> existingParts =
                    certificatePartRepository.findByCertificate_CertificateIdOrderByPartOrderAsc(certificateId);

            if (existingParts.isEmpty()) {
                // Chứng chỉ chưa có phần nào -> tạo mới toàn bộ
                List<CertificatePart> newParts = new ArrayList<>();
                int order = 1;
                for (CreateCertificateRequest.PartRequest partRequest : request.getParts()) {
                    newParts.add(CertificatePart.builder()
                            .certificate(certificate)
                            .partName(partRequest.getPartName())
                            .partDescription(partRequest.getPartDescription())
                            .partOrder(order++)
                            .properties(normalizeProperties(partRequest.getProperties()))
                            .build());
                }
                certificatePartRepository.saveAll(newParts);
            } else {
                // Đã có phần -> cập nhật nội dung từng phần hoặc thêm mới nếu thiếu
                int order = 1;
                List<CertificatePart> toSave = new ArrayList<>();
                for (int i = 0; i < request.getParts().size(); i++) {
                    CreateCertificateRequest.PartRequest pr = request.getParts().get(i);
                    CertificatePart part;
                    if (i < existingParts.size()) {
                        part = existingParts.get(i);
                        part.setPartName(pr.getPartName());
                        part.setPartDescription(pr.getPartDescription());
                        part.setPartOrder(order++);
                        part.setProperties(normalizeProperties(pr.getProperties()));
                    } else {
                        part = CertificatePart.builder()
                                .certificate(certificate)
                                .partName(pr.getPartName())
                                .partDescription(pr.getPartDescription())
                                .partOrder(order++)
                                .properties(normalizeProperties(pr.getProperties()))
                                .build();
                    }
                    toSave.add(part);
                }
                // Nếu danh sách mới ít hơn danh sách cũ, xoá các phần dư
                if (existingParts.size() > request.getParts().size()) {
                    List<CertificatePart> toRemove =
                            existingParts.subList(request.getParts().size(), existingParts.size());
                    certificatePartRepository.deleteAll(toRemove);
                }
                certificatePartRepository.saveAll(toSave);
            }
        }

        List<CertificatePart> parts =
                certificatePartRepository.findByCertificate_CertificateIdOrderByPartOrderAsc(certificateId);

        log.info("Đã cập nhật chứng chỉ: certificateId={}, số phần={}", certificateId, parts.size());

        return buildCertificateDetail(certificate, parts);
    }

    @Override
    @Transactional
    public void deleteCertificate(UUID certificateId) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new AppException(ErrorCode.CERTIFICATE_NOT_FOUND));

        if (courseRepository.existsByCertificate_CertificateId(certificateId)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Không thể xoá chứng chỉ đang được sử dụng bởi một hoặc nhiều khoá học");
        }

        certificatePartRepository.deleteByCertificate_CertificateId(certificateId);
        certificateRepository.delete(certificate);
        log.info("Đã xoá chứng chỉ thành công: certificateId={}", certificateId);
    }

    private String normalizeProperties(String raw) {
        try {
            return JsonUtil.normalize(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Dữ liệu properties của phần không phải JSON hợp lệ");
        }
    }

    private CertificateResponse buildCertificateSummary(Certificate certificate) {
        // Dùng cho danh sách - tính số phần (partCount)
        int count = certificate.getParts() != null ? certificate.getParts().size() : 0;
        return CertificateResponse.builder()
                .certificateId(certificate.getCertificateId())
                .certificateName(certificate.getCertificateName())
                .description(certificate.getDescription())
                .partCount(count)
                .createdAt(certificate.getCreatedAt())
                .lastUpdated(certificate.getLastUpdated())
                .build();
    }

    private CertificateResponse buildCertificateDetail(Certificate certificate, List<CertificatePart> parts) {
        List<CertificatePartResponse> partResponses = parts.stream()
                .map(this::toPartResponse)
                .toList();

        return CertificateResponse.builder()
                .certificateId(certificate.getCertificateId())
                .certificateName(certificate.getCertificateName())
                .description(certificate.getDescription())
                .partCount(partResponses.size())
                .parts(partResponses)
                .createdAt(certificate.getCreatedAt())
                .lastUpdated(certificate.getLastUpdated())
                .build();
    }

    private CertificatePartResponse toPartResponse(CertificatePart part) {
        return CertificatePartResponse.builder()
                .partId(part.getPartId())
                .certificateId(part.getCertificate() != null ? part.getCertificate().getCertificateId() : null)
                .partName(part.getPartName())
                .partDescription(part.getPartDescription())
                .partOrder(part.getPartOrder())
                .properties(JsonUtil.parse(part.getProperties()))
                .build();
    }
}