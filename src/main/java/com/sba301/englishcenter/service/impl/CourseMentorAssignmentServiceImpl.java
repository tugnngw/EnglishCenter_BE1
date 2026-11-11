package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.Enrollment;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.EnrollmentRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.service.CourseMentorAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Triển khai {@link CourseMentorAssignmentService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseMentorAssignmentServiceImpl implements CourseMentorAssignmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public int assignMentorToStudentCourses(UUID studentId, UUID mentorId) {
        if (studentId == null || mentorId == null) {
            return 0;
        }
        User mentor = userRepository.findById(mentorId).orElse(null);
        if (mentor == null) {
            log.warn("Không tìm thấy mentor {} để gán vào khóa học của học viên {}", mentorId, studentId);
            return 0;
        }
        List<Enrollment> enrollments =
                enrollmentRepository.findByStudentIdAndStatus(studentId, Enrollment.Status.active);
        int assigned = 0;
        for (Enrollment enrollment : enrollments) {
            Course course = enrollment.getCourse();
            if (course == null || course.getMentor() != null) {
                // Khóa học đã có mentor → không ghi đè.
                continue;
            }
            course.setMentor(mentor);
            courseRepository.save(course);
            assigned++;
            log.info("Gán mentor {} vào khóa học {} của học viên {}",
                    mentorId, course.getCourseId(), studentId);
        }
        return assigned;
    }
}
