package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, LessonProgress.LessonProgressId> {

    Optional<LessonProgress> findByContent_ContentIdAndStudent_UserId(UUID contentId, UUID studentId);

    /** Tiến độ của học viên cho một khóa học (dùng để tính "đã học hết video chưa"). */
    List<LessonProgress> findByStudent_UserIdAndContent_Course_CourseId(UUID studentId, UUID courseId);

    /** Số bài học của khóa mà học viên đã xem xong. */
    long countByStudent_UserIdAndContent_Course_CourseIdAndCompletedTrue(UUID studentId, UUID courseId);
}
