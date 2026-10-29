package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.HomeworkSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID> {

    List<HomeworkSubmission> findByStudent_UserIdOrderBySubmittedDateDesc(UUID studentId);

    Optional<HomeworkSubmission> findByHomeworkContent_ContentIdAndStudent_UserId(UUID contentId, UUID studentId);

    /** Tất cả lần nộp của học viên cho một bài tập — mới nhất trước. */
    List<HomeworkSubmission> findByHomeworkContent_ContentIdAndStudent_UserIdOrderBySubmittedDateDesc(
            UUID contentId, UUID studentId);

    /** Số lần đã nộp của một học viên cho một bài tập — kiểm tra giới hạn maxAttempts. */
    long countByHomeworkContent_ContentIdAndStudent_UserId(UUID contentId, UUID studentId);

    /* =====================================================
     * Hàng đợi chấm của mentor
     * ===================================================== */

    /**
     * Bài nộp chưa chấm (graded_date IS NULL) — mới nhất trước.
     * <p>
     * Bắt buộc phải có {@code @Query}: {@code findPendingGrading} KHÔNG phải
     * tên derived query hợp lệ (Spring Data sẽ parse thành property
     * {@code pendingGrading} trên entity → không tồn tại → lỗi
     * {@code QueryCreationException} lúc khởi động ứng dụng).
     */
    @EntityGraph(attributePaths = {"homeworkContent", "homeworkContent.course", "student"})
    @Query("select s from HomeworkSubmission s "
            + "where s.gradedDate is null "
            + "order by s.submittedDate desc")
    Page<HomeworkSubmission> findPendingGrading(Pageable pageable);

    /** Bài nộp chưa chấm của các khóa học mà mentor này phụ trách. */
    @EntityGraph(attributePaths = {"homeworkContent", "homeworkContent.course", "student"})
    @Query("select s from HomeworkSubmission s where s.gradedDate is null "
            + "and s.homeworkContent.course.mentor.userId = :mentorId "
            + "order by s.submittedDate desc")
    Page<HomeworkSubmission> findPendingGradingForMentor(@Param("mentorId") UUID mentorId, Pageable pageable);

    /** Tất cả bài nộp của các khóa học mà mentor này phụ trách. */
    @EntityGraph(attributePaths = {"homeworkContent", "homeworkContent.course", "student"})
    @Query("select s from HomeworkSubmission s "
            + "where s.homeworkContent.course.mentor.userId = :mentorId "
            + "order by s.submittedDate desc")
    Page<HomeworkSubmission> findAllForMentor(@Param("mentorId") UUID mentorId, Pageable pageable);
}