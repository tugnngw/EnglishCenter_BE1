package com.sba301.englishcenter.specification;

import com.sba301.englishcenter.common.enums.CourseStatus;
import com.sba301.englishcenter.entity.Course;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CourseSpecification {

    private CourseSpecification() {
    }

    /**
     * Build dynamic specification for course filtering.
     *
     * @param keyword      Search in title, description, code
     * @param certificateId Filter theo chứng chỉ
     * @param certificatePartId Filter theo phần của chứng chỉ
     * @param level        Filter theo level (BEGINNER / INTERMEDIATE / ADVANCED)
     * @param targetScore  Lọc theo điểm mục tiêu tối thiểu
     * @param status       Lọc theo trạng thái khóa học
     * @return Combined specification
     */
    public static Specification<Course> filterCourses(String keyword, UUID certificateId,
                                                      UUID certificatePartId, String level,
                                                      BigDecimal targetScore, String status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Keyword search (title, description, code)
            if (keyword != null && !keyword.trim().isEmpty()) {
                String likePattern = "%" + keyword.toLowerCase() + "%";
                Predicate titlePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")), likePattern);
                Predicate descriptionPredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("description")), likePattern);
                Predicate codePredicate = criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("code")), likePattern);

                predicates.add(criteriaBuilder.or(titlePredicate, descriptionPredicate, codePredicate));
            }

            // Filter by certificate
            if (certificateId != null) {
                predicates.add(criteriaBuilder.equal(root.get("certificate").get("certificateId"), certificateId));
            }

            // Filter by certificate part
            if (certificatePartId != null) {
                predicates.add(criteriaBuilder.equal(root.get("certificatePart").get("partId"), certificatePartId));
            }

            // Filter by level
            if (level != null && !level.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.upper(root.get("level")), level.trim().toUpperCase()));
            }

            // Filter by minimum target score
            if (targetScore != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("targetScore"), targetScore));
            }

            // Filter by status (status dùng AttributeConverter nên so sánh với kiểu enum Java)
            if (status != null && !status.trim().isEmpty()) {
                CourseStatus statusEnum = parseStatus(status);
                if (statusEnum != null) {
                    predicates.add(criteriaBuilder.equal(root.get("status"), statusEnum));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Parse chuỗi trạng thái từ query param sang enum, hỗ trợ cả 'active' và 'ACTIVE'.
     * Trả về null nếu không hợp lệ (bỏ qua filter thay vì ném lỗi).
     */
    private static CourseStatus parseStatus(String status) {
        try {
            return CourseStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}