package com.sba301.englishcenter.specification;

import com.sba301.englishcenter.common.enums.PracticeExamType;
import com.sba301.englishcenter.entity.PracticeExam;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/**
 * Bộ lọc kho đề luyện tập: theo kỹ năng và từ khóa (tiêu đề / tên bộ đề). Tham số null thì bỏ qua.
 */
public final class PracticeExamSpecification {

    private PracticeExamSpecification() {
    }

    public static Specification<PracticeExam> filter(PracticeExamType type, String keyword) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("examType"), type));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("bankName"), "")), like)));
            }
            return predicate;
        };
    }
}
