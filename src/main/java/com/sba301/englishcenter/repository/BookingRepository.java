package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    // Học viên đã có booking (theo các status truyền vào) chồng giờ chưa: start < newEnd VÀ end > newStart
    boolean existsByStudentIdAndStatusInAndSchedule_StartTimeLessThanAndSchedule_EndTimeGreaterThan(
            UUID studentId, Collection<BookingStatus> statuses, LocalDateTime newEnd, LocalDateTime newStart);

    // Giống trên nhưng bỏ qua booking đang đổi lịch
    boolean existsByStudentIdAndBookingIdNotAndStatusInAndSchedule_StartTimeLessThanAndSchedule_EndTimeGreaterThan(
            UUID studentId, UUID bookingId, Collection<BookingStatus> statuses,
            LocalDateTime newEnd, LocalDateTime newStart);

    // Job nhắc lịch: booking (theo status) chưa được nhắc, giờ bắt đầu trong (from, to].
    // Khóa dòng để 2 instance chạy job cùng lúc không gửi nhắc trùng
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Booking> findByStatusAndReminderSentAtIsNullAndSchedule_StartTimeGreaterThanAndSchedule_StartTimeLessThanEqual(
            BookingStatus status, LocalDateTime from, LocalDateTime to);

    // Slot còn booking nào khác status truyền vào (vd khác cancelled) không - dùng khi xóa slot
    boolean existsBySchedule_ScheduleIdAndStatusNot(UUID scheduleId, BookingStatus status);
}
