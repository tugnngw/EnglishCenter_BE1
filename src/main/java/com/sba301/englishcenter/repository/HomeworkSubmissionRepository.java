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

    /** Tất cả lần nộp của MỌI học viên cho một bài tập — mới nhất trước (dùng thống kê). */
    List<HomeworkSubmission> findByHomeworkContent_ContentIdOrderBySubmittedDateDesc(UUID contentId);

    /** Số lần đã nộp của một học viên cho một bài tập — kiểm tra giới hạn maxAttempts. */
    long countByHomeworkContent_ContentIdAndStudent_UserId(UUID contentId, UUID studentId);

    /** Bài tập đã có ai nộp chưa: khóa sửa hình thức nộp / chặn xoá bài tập. */
    boolean existsByHomeworkContent_ContentId(UUID contentId);

    /** Số bài nộp của 1 bài tập (trang Homework của mentor, V27). */
    long countByHomeworkContent_ContentId(UUID contentId);

    /** Học viên đã có bài nộp được chấm ĐẠT cho bài tập này (chặng HOMEWORK của lộ trình hoàn thành, V27). */
    boolean existsByHomeworkContent_ContentIdAndStudent_UserIdAndPassedTrue(UUID contentId, UUID studentId);

    /**
     * Bài nộp CHƯA CHẤM của học viên mà CHƯA được gán mentor nào.
     * <p>
     * Dùng để gán lại mentor khi học viên mua gói 1-1 SAU khi đã nộp bài
     * (lúc nộp chưa có mentor → {@code assigned_grader_id} null).
     * Bài trắc nghiệm không cần chấm tay nên bị loại.
     */
    @EntityGraph(attributePaths = {"homeworkContent", "homeworkContent.course", "student"})
    @Query("select s from HomeworkSubmission s "
            + "where s.student.userId = :studentId "
            + "and s.gradedDate is null "
            + "and s.assignedGraderId is null "
            + "and (s.submissionType is null or lower(s.submissionType) <> 'multiple_choice') "
            + "order by s.submittedDate desc")
    List<HomeworkSubmission> findPendingByStudentIdWithoutGrader(@Param("studentId") UUID studentId);

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

    /**
     * Hàng đợi chấm TAY của mentor — CHỈ bài của học viên ĐÃ MUA GÓI MENTOR 1-1.
     * <p>
     * Điều kiện:
     * <ul>
     *   <li>Bài được GIAO cho mentor này ({@code assigned_grader_id = :mentorId}) —
     *       mentor đó là người được giao chấm bài (snapshot lúc học viên nộp);</li>
     *   <li>HOẶC bài CHƯA gán ai của học viên mà mentor này đang có gói kèm 1-1
     *       còn hiệu lực → trường hợp học viên mua gói SAU khi đã nộp bài, hoặc AI
     *       chấm lỗi. Bài này không có ai chấm nên mentor phải tiếp tục được thấy.</li>
     * </ul>
     * Học viên KHÔNG mua mentor → AI chấm tự động → không có trong hàng đợi của mentor.
     * <p>
     * Bài TRẮC NGHIỆM (Nhóm A) bị loại ở đây — hệ thống tự chấm, mentor chỉ xem
     * bảng thống kê kết quả làm bài.
     */
    @EntityGraph(attributePaths = {"homeworkContent", "homeworkContent.course", "student"})
    @Query("select s from HomeworkSubmission s "
            + "where s.gradedDate is null "
            + "and (s.assignedGraderId = :mentorId "
            + "   or (s.assignedGraderId is null and exists (select 1 from MentoringPlan p "
            + "      where p.mentorId = :mentorId and p.studentId = s.student.userId "
            + "      and p.status in :planStatuses))) "
            + "and (s.submissionType is null or lower(s.submissionType) <> 'multiple_choice') "
            + "order by s.submittedDate desc")
    Page<HomeworkSubmission> findPendingGradingForMentorWithStudents(
            @Param("mentorId") UUID mentorId,
            @Param("planStatuses") java.util.Collection<com.sba301.englishcenter.common.enums.MentoringPlanStatus> planStatuses,
            Pageable pageable);

    /** Tất cả bài nộp của các khóa học mà mentor này phụ trách. */
    @EntityGraph(attributePaths = {"homeworkContent", "homeworkContent.course", "student"})
    @Query("select s from HomeworkSubmission s "
            + "where s.homeworkContent.course.mentor.userId = :mentorId "
            + "order by s.submittedDate desc")
    Page<HomeworkSubmission> findAllForMentor(@Param("mentorId") UUID mentorId, Pageable pageable);

    /**
     * Tất cả bài mentor này được phép chấm: bài được GIAO ({@code assigned_grader_id})
     * + bài CHƯA gán ai của khóa học mentor phụ trách hoặc của học viên mentor
     * dạy kèm 1-1 (fallback khi AI chấm lỗi).
     * <p>
     * Bài trắc nghiệm (Nhóm A) vẫn trả về — mentor xem để thống kê, không chấm tay.
     */
    @EntityGraph(attributePaths = {"homeworkContent", "homeworkContent.course", "student"})
    @Query("select s from HomeworkSubmission s "
            // Join tường minh từng bước: bài tập của mentor (V27) không có khóa học (course NULL) vẫn phải ra
            + "left join s.homeworkContent hc left join hc.course c left join c.mentor m "
            + "where (s.assignedGraderId = :mentorId "
            + " or (s.assignedGraderId is null and (m.userId = :mentorId "
            + "   or exists (select 1 from MentoringPlan p "
            + "      where p.mentorId = :mentorId and p.studentId = s.student.userId "
            + "      and p.status in :planStatuses)))) "
            + "order by s.submittedDate desc")
    Page<HomeworkSubmission> findAllForMentorWithStudents(
            @Param("mentorId") UUID mentorId,
            @Param("planStatuses") java.util.Collection<com.sba301.englishcenter.common.enums.MentoringPlanStatus> planStatuses,
            Pageable pageable);
}