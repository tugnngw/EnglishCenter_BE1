package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.common.enums.ConsultationStatus;
import com.sba301.englishcenter.entity.ConsultationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsultationRequestRepository extends JpaRepository<ConsultationRequest, UUID> {

    // Yêu cầu đang mở (pending / contacted) của học viên - mỗi học viên tối đa 1 (V21 uq_consultation_open)
    Optional<ConsultationRequest> findFirstByStudentIdAndStatusIn(UUID studentId, Collection<ConsultationStatus> statuses);

    // Yêu cầu gần nhất của học viên (mọi trạng thái)
    Optional<ConsultationRequest> findFirstByStudentIdOrderByCreatedAtDesc(UUID studentId);

    Page<ConsultationRequest> findByStatusOrderByCreatedAtDesc(ConsultationStatus status, Pageable pageable);

    // Yêu cầu mentor này đang phụ trách
    Page<ConsultationRequest> findByMentorIdOrderByLastUpdatedDesc(UUID mentorId, Pageable pageable);

    // Mentor đã nhận yêu cầu tư vấn của học viên này chưa (= đang phụ trách)
    boolean existsByMentorIdAndStudentId(UUID mentorId, UUID studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ConsultationRequest> findWithLockByRequestId(UUID requestId);
}
