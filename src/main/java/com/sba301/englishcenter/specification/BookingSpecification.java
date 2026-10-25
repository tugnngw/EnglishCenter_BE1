package com.sba301.englishcenter.specification;

import com.sba301.englishcenter.common.enums.BookingStatus;
import com.sba301.englishcenter.entity.Booking;
import com.sba301.englishcenter.entity.MentorSchedule;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BookingSpecification {

    // Booking còn hiệu lực (chưa hủy, chưa có kết quả)
    private static final List<BookingStatus> ACTIVE_STATUSES = List.of(BookingStatus.pending, BookingStatus.confirmed);

    private BookingSpecification() {
    }

    /**
     * Lịch của học viên (TASK D5-12).
     *
     * @param past false = sắp tới (còn hiệu lực và chưa kết thúc), true = các booking còn lại
     */
    public static Specification<Booking> forStudent(UUID studentId, boolean past, LocalDateTime now) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("studentId"), studentId),
                timePredicate(root, criteriaBuilder, past, now));
    }

    /**
     * Lịch dạy của mentor (TASK D5-13), cùng quy tắc sắp tới / đã qua như học viên.
     */
    public static Specification<Booking> forMentor(UUID mentorId, boolean past, LocalDateTime now) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("mentorId"), mentorId),
                timePredicate(root, criteriaBuilder, past, now));
    }

    /**
     * Admin lọc booking theo trạng thái, mentor, học viên (TASK D5-14). Tham số null thì bỏ qua.
     */
    public static Specification<Booking> filterForAdmin(BookingStatus status, UUID mentorId, UUID studentId) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (mentorId != null) {
                predicates.add(criteriaBuilder.equal(root.get("mentorId"), mentorId));
            }
            if (studentId != null) {
                predicates.add(criteriaBuilder.equal(root.get("studentId"), studentId));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    // Sắp tới = còn hiệu lực VÀ giờ kết thúc sau "now"; đã qua = phần còn lại
    private static Predicate timePredicate(Root<Booking> root, CriteriaBuilder criteriaBuilder,
                                           boolean past, LocalDateTime now) {
        Path<LocalDateTime> endTime = root.<MentorSchedule>get("schedule").get("endTime");
        Predicate upcoming = criteriaBuilder.and(
                root.get("status").in(ACTIVE_STATUSES),
                criteriaBuilder.greaterThan(endTime, now));
        return past ? criteriaBuilder.not(upcoming) : upcoming;
    }
}
