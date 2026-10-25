package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.common.enums.RepeatRule;
import com.sba301.englishcenter.common.enums.StudyStatus;
import com.sba301.englishcenter.entity.StudySchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudyScheduleRepository extends JpaRepository<StudySchedule, UUID> {

    // Lịch của học viên trong khoảng [from, to), sắp theo giờ học (sort truyền qua Pageable)
    Page<StudySchedule> findByStudentIdAndScheduledTimeGreaterThanEqualAndScheduledTimeLessThan(
            UUID studentId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Như trên, lọc thêm trạng thái
    Page<StudySchedule> findByStudentIdAndStatusAndScheduledTimeGreaterThanEqualAndScheduledTimeLessThan(
            UUID studentId, StudyStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);

    // Ownership: chỉ lấy lịch của đúng học viên
    Optional<StudySchedule> findByScheduleIdAndStudentId(UUID scheduleId, UUID studentId);

    // Job nhắc: lịch đang chờ, chưa nhắc, giờ học trong (now, now + max phút nhắc]. Khóa dòng để không gửi trùng
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StudySchedule> findByStatusAndReminderSentAtIsNullAndScheduledTimeGreaterThanAndScheduledTimeLessThanEqual(
            StudyStatus status, LocalDateTime from, LocalDateTime to);

    // Job lặp lại: lịch daily/weekly đang chờ mà giờ học đã qua -> chuyển sang lần kế tiếp
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StudySchedule> findByStatusAndRepeatRuleNotAndScheduledTimeLessThanEqual(
            StudyStatus status, RepeatRule repeatRule, LocalDateTime now);
}
