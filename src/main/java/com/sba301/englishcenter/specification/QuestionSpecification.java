package com.sba301.englishcenter.specification;

import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.entity.Question;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuestionSpecification {

    private QuestionSpecification() {
    }

    /** Lọc ngân hàng câu hỏi để admin chọn câu đưa vào đề. */
    public static Specification<Question> filterQuestions(String keyword, QuestionType type,
                                                          DifficultyLevel difficulty, UUID certificateId,
                                                          UUID certificatePartId, String topic) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("questionText")),
                        "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("questionType"), type));
            }
            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficultyLevel"), difficulty));
            }
            if (certificateId != null) {
                predicates.add(cb.equal(root.get("certificate").get("certificateId"), certificateId));
            }
            if (certificatePartId != null) {
                predicates.add(cb.equal(root.get("certificatePart").get("partId"), certificatePartId));
            }
            if (topic != null && !topic.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("topic")), topic.trim().toLowerCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
