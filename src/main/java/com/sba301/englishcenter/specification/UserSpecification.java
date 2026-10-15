package com.sba301.englishcenter.specification;

import com.sba301.englishcenter.entity.Role;
import com.sba301.englishcenter.entity.User;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    private UserSpecification() {
    }

    /**
     * Build dynamic specification for user filtering.
     *
     * @param keyword Search in email, fullName, phone
     * @param roleName Filter by role name (exact match)
     * @param isActive Filter by active status
     * @return Combined specification
     */
    public static Specification<User> filterUsers(String keyword, String roleName, Boolean isActive) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Keyword search (email, fullName, phone)
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                Predicate emailPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("email")), likePattern);
                Predicate namePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("fullName")), likePattern);
                Predicate phonePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("phone")), likePattern);

                predicates.add(criteriaBuilder.or(emailPredicate, namePredicate, phonePredicate));
            }

            // Filter by role name
            if (roleName != null && !roleName.trim().isEmpty()) {
                Join<User, Role> roleJoin = root.join("role");
                predicates.add(criteriaBuilder.equal(roleJoin.get("roleName"), roleName));
            }

            // Filter by active status
            if (isActive != null) {
                predicates.add(criteriaBuilder.equal(root.get("isActive"), isActive));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
