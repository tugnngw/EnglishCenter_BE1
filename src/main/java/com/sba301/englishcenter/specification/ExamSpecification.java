package com.sba301.englishcenter.specification;

import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.entity.Enrollment;
import com.sba301.englishcenter.entity.Exam;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExamSpecification {

    private ExamSpecification() {
    }

    /**
     * Build dynamic specification for exam filtering.
     *
     * @param keyword       Search in title, code, description
     * @param examType      Loại đề (quiz / midterm / final / placement_test)
     * @param status        Trạng thái đề (public API luôn ép = published)
     * @param examScope     trial / course / practice
     * @param certificateId Lọc theo chứng chỉ
     * @param courseId      Lọc theo khóa học
     * @param level         Lọc theo recommended_level (không phân biệt hoa thường)
     * @param difficulty    Lọc theo độ khó
     * @param isFree        Lọc đề miễn phí / trả phí
     */
    public static Specification<Exam> filterExams(String keyword, ExamType examType, ExamStatus status,
                                                  ExamScope examScope, UUID certificateId, UUID courseId,
                                                  String level, DifficultyLevel difficulty, Boolean isFree) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (keyword != null && !keyword.trim().isEmpty()) {
                String likePattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("examTitle")), likePattern),
                        cb.like(cb.lower(root.get("examCode")), likePattern),
                        cb.like(cb.lower(root.get("description")), likePattern)));
            }
            if (examType != null) {
                // exam_type không map NAMED_ENUM (xem Exam.examType): nối với '' để PostgreSQL đổi cột enum sang
                // text trước khi so sánh (cb.as(String) bị Hibernate bỏ qua vì thuộc tính đã là String sau converter)
                predicates.add(cb.equal(cb.concat(root.get("examType"), ""), examType.getValue()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (examScope != null) {
                predicates.add(cb.equal(root.get("examScope"), examScope));
            } else {
                // Đề luyện tập AI chỉ hiện ở trang Luyện tập AI (lọc examScope = practice), không lẫn vào ngân hàng đề thi
                predicates.add(cb.notEqual(root.get("examScope"), ExamScope.PRACTICE));
            }
            if (certificateId != null && examScope == ExamScope.PRACTICE) {
                // Đề luyện tập theo mục tiêu: đề của chứng chỉ đó + đề không gắn chứng chỉ (dùng chung)
                var certificate = root.join("certificate", JoinType.LEFT);
                predicates.add(cb.or(cb.equal(certificate.get("certificateId"), certificateId),
                        cb.isNull(certificate.get("certificateId"))));
            } else if (certificateId != null) {
                predicates.add(cb.equal(root.get("certificate").get("certificateId"), certificateId));
            }
            if (courseId != null) {
                predicates.add(cb.equal(root.get("course").get("courseId"), courseId));
            }
            if (level != null && !level.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("recommendedLevel")), level.trim().toUpperCase()));
            }
            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficultyLevel"), difficulty));
            }
            if (isFree != null) {
                predicates.add(cb.equal(root.get("isFree"), isFree));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Đề học viên được thấy trong danh sách:
     * <ul>
     *     <li>đề khóa học (course): chỉ khi đang ghi danh (active) khóa đó;</li>
     *     <li>đề thi thử / kiểm tra đầu vào (trial): đã bỏ, không hiện nữa;</li>
     *     <li>đề luyện tập AI (practice): giữ nguyên cách lọc của trang Luyện tập AI.</li>
     * </ul>
     *
     * @param userId học viên đang đăng nhập, {@code null} = khách (không thấy đề khóa học nào)
     */
    public static Specification<Exam> visibleToStudent(UUID userId) {
        return (root, query, cb) -> {
            Predicate practice = cb.equal(root.get("examScope"), ExamScope.PRACTICE);
            if (userId == null) {
                return practice;
            }
            Subquery<Integer> enrolled = query.subquery(Integer.class);
            Root<Enrollment> enrollment = enrolled.from(Enrollment.class);
            enrolled.select(cb.literal(1)).where(
                    cb.equal(enrollment.get("studentId"), userId),
                    cb.equal(enrollment.get("course"), root.get("course")),
                    cb.equal(enrollment.get("status"), Enrollment.Status.active));
            Predicate course = cb.and(cb.equal(root.get("examScope"), ExamScope.COURSE), cb.exists(enrolled));
            return cb.or(practice, course);
        };
    }
}
