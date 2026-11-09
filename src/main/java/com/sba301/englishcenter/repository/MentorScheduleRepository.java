package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.common.enums.ScheduleStatus;
import com.sba301.englishcenter.entity.MentorSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorScheduleRepository extends JpaRepository<MentorSchedule, UUID> {

    // Các buổi của 1 gói kèm 1-1, theo giờ bắt đầu (V21)
    List<MentorSchedule> findByPlanIdOrderByStartTimeAsc(UUID planId);

    // Mentor có buổi nào (khác status truyền vào) chồng giờ không: start < newEnd VÀ end > newStart
    boolean existsByMentorIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID mentorId, ScheduleStatus status, LocalDateTime newEnd, LocalDateTime newStart);

    // Giống trên nhưng bỏ qua chính buổi đang dời lịch (V26)
    boolean existsByMentorIdAndScheduleIdNotAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID mentorId, UUID scheduleId, ScheduleStatus status, LocalDateTime newEnd, LocalDateTime newStart);

    // Khóa dòng khi thanh toán / hủy / ghi nhận buổi học (TASK D5-07)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MentorSchedule> findWithLockByScheduleId(UUID scheduleId);
}
