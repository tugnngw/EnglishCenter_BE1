package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.common.enums.ScheduleStatus;
import com.sba301.englishcenter.entity.MentorSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MentorScheduleRepository extends JpaRepository<MentorSchedule, UUID> {

    // Slot chưa kết thúc của 1 mentor (mọi trạng thái) - mentor tự quản lý slot
    Page<MentorSchedule> findByMentorIdAndEndTimeAfter(UUID mentorId, LocalDateTime now, Pageable pageable);

    // Slot của 1 mentor theo các trạng thái, bắt đầu trong khoảng [from, to) - học viên xem lịch trống
    List<MentorSchedule> findByMentorIdAndStatusInAndStartTimeGreaterThanEqualAndStartTimeLessThanOrderByStartTimeAsc(
            UUID mentorId, Collection<ScheduleStatus> statuses, LocalDateTime from, LocalDateTime to);

    // Mentor có slot nào (khác status truyền vào) chồng giờ không: start < newEnd VÀ end > newStart
    boolean existsByMentorIdAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID mentorId, ScheduleStatus status, LocalDateTime newEnd, LocalDateTime newStart);

    // Giống trên nhưng bỏ qua chính slot đang sửa
    boolean existsByMentorIdAndScheduleIdNotAndStatusNotAndStartTimeLessThanAndEndTimeGreaterThan(
            UUID mentorId, UUID scheduleId, ScheduleStatus status, LocalDateTime newEnd, LocalDateTime newStart);

    // Khóa dòng khi đặt / hủy / đổi lịch, tránh 2 học viên đặt cùng 1 slot cùng lúc (TASK D5-07)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MentorSchedule> findWithLockByScheduleId(UUID scheduleId);
}
