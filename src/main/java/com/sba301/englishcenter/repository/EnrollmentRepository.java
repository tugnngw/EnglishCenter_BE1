package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.Enrollment;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    boolean existsByStudentIdAndCourse_CourseIdAndStatus(UUID studentId, UUID courseId, Enrollment.Status status);
    @EntityGraph(attributePaths = "course")
    Page<Enrollment> findByStudentIdAndStatus(UUID studentId, Enrollment.Status status, Pageable pageable);
}
