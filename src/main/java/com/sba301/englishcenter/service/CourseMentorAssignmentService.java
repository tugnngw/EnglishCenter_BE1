package com.sba301.englishcenter.service;

import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.Enrollment;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.repository.CourseRepository;
import com.sba301.englishcenter.repository.EnrollmentRepository;
import com.sba301.englishcenter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Gán mentor 1-1 của học viên vào khóa học khi học viên mua gói kèm 1-1.
 * <p>
 * Quy tắc đã chốt: khóa học tạo ra KHÔNG có mentor sẵn. Học viên mua gói mentor 1-1
 * thì mentor đó được thêm vào các khóa học mà học viên đang học → mentor đó chấm
 * bài tập của học viên trong khóa học đó.
 * <p>
 * Nếu khóa học ĐÃ có mentor (admin gán), không ghi đè — gói 1-1 vẫn có hiệu lực
 * riêng (xem {@code homework_submissions.assigned_grader_id}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseMentorAssignmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    /**
     * Gán mentor 1-1 của học viên vào các khóa học học viên đang học.
     * <p>
     * Chỉ gán cho khóa học CHƯA có mentor. Khóa học đã có mentor (do admin gán)
     * thì giữ nguyên.
     *
     * @param studentId học viên vừa thanh toán gói 1-1
     * @param mentorId  mentor 1-1 của học viên
     * @return số khóa học đã được gán mentor
     */
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
