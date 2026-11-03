package com.sba301.englishcenter.specification;

import com.sba301.englishcenter.entity.MentorProfile;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MentorSpecification {

    private MentorSpecification() {
    }

    /**
     * Build dynamic specification for mentor filtering.
     *
     * @param specialization Filter by specialization (partial match)
     * @param minRate Minimum hourly rate
     * @param maxRate Maximum hourly rate
     * @return Combined specification
     */
    public static Specification<MentorProfile> filterMentors(String specialization, BigDecimal minRate, BigDecimal maxRate) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only active mentors with MENTOR role
            predicates.add(criteriaBuilder.equal(root.get("user").get("isActive"), true));
            predicates.add(criteriaBuilder.equal(root.get("user").get("role").get("roleName"), "MENTOR"));

            // Filter by specialization
            if (specialization != null && !specialization.trim().isEmpty()) {
                String likePattern = "%" + specialization.toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("specialization")), likePattern));
            }

            // Filter by hourly rate range
            if (minRate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("hourlyRate"), minRate));
            }
            if (maxRate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("hourlyRate"), maxRate));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Admin: lọc mentor (cả active / inactive). Tham số null / rỗng = bỏ qua.
     *
     * @param keyword        tìm gần đúng theo tên, email, số điện thoại
     * @param department     khớp chính xác (không phân biệt hoa thường, bỏ khoảng trắng 2 đầu)
     * @param specialization tìm gần đúng
     * @param isActive       true = đang hoạt động, false = tạm nghỉ, null = tất cả
     */
    public static Specification<MentorProfile> filterMentorsForAdmin(String keyword, String department,
                                                                     String specialization, Boolean isActive) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("user").get("role").get("roleName"), "MENTOR"));

            if (isActive != null) {
                predicates.add(criteriaBuilder.equal(root.get("user").get("isActive"), isActive));
            }

            if (hasText(keyword)) {
                String likePattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("user").<String>get("fullName")), likePattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("user").<String>get("email")), likePattern),
                        criteriaBuilder.like(root.get("user").<String>get("phone"), likePattern)));
            }

            if (hasText(department)) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(criteriaBuilder.trim(root.<String>get("department"))),
                        department.trim().toLowerCase()));
            }

            if (hasText(specialization)) {
                String likePattern = "%" + specialization.trim().toLowerCase() + "%";
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.<String>get("specialization")), likePattern));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
