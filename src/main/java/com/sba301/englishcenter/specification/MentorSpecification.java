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
}
