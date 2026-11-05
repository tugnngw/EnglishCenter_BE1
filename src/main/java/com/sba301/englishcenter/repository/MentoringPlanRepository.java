package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.MentoringPlan;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentoringPlanRepository extends JpaRepository<MentoringPlan, UUID> {

    // Mentor xem các gói mình đã tạo, mới nhất trước
    Page<MentoringPlan> findByMentorIdOrderByCreatedAtDesc(UUID mentorId, Pageable pageable);

    // Học viên xem các gói của mình, mới nhất trước
    List<MentoringPlan> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    // Khóa dòng khi thanh toán / hủy gói, tránh webhook và thao tác hủy chạy cùng lúc
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MentoringPlan> findWithLockByPlanId(UUID planId);
}
