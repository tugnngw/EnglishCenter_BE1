package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.Enrollment;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID>, JpaSpecificationExecutor<Enrollment> {
    boolean existsByStudentIdAndCourse_CourseIdAndStatus(UUID studentId, UUID courseId, Enrollment.Status status);

    @EntityGraph(attributePaths = "course")
    Page<Enrollment> findByStudentIdAndStatus(UUID studentId, Enrollment.Status status, Pageable pageable);

    /** Các khóa học học viên đang học (đủ để gán mentor 1-1 vào khóa học khi mua gói). */
    @EntityGraph(attributePaths = "course")
    List<Enrollment> findByStudentIdAndStatus(UUID studentId, Enrollment.Status status);
}
