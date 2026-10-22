package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.CertificatePart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CertificatePartRepository extends JpaRepository<CertificatePart, UUID> {

    List<CertificatePart> findByCertificate_CertificateIdOrderByPartOrderAsc(UUID certificateId);

    void deleteByCertificate_CertificateId(UUID certificateId);
}