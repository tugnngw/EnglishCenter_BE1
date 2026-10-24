package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.entity.ExamAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    /**
     * Khóa dòng lượt thi (SELECT ... FOR UPDATE). Mọi thao tác ghi lên một lượt thi (lưu đáp án, nộp bài,
     * chấm tay) đều đi qua đây nên không thể xen kẽ nhau: nộp trùng / lưu đáp án đồng thời sẽ được tuần tự hóa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a where a.attemptId = :attemptId")
    Optional<ExamAttempt> findByIdForUpdate(@Param("attemptId") UUID attemptId);

    Optional<ExamAttempt> findFirstByExam_ExamIdAndStudent_UserIdAndStatus(UUID examId, UUID studentId,
                                                                           AttemptStatus status);

    long countByExam_ExamIdAndStudent_UserId(UUID examId, UUID studentId);

    boolean existsByExam_ExamId(UUID examId);

    @Query("select coalesce(max(a.attemptNumber), 0) from ExamAttempt a "
            + "where a.exam.examId = :examId and a.student.userId = :studentId")
    int findMaxAttemptNumber(@Param("examId") UUID examId, @Param("studentId") UUID studentId);

    /** Lịch sử thi của học viên (mới nhất trước). */
    @EntityGraph(attributePaths = {"exam"})
    Page<ExamAttempt> findByStudent_UserId(UUID studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"exam"})
    Page<ExamAttempt> findByStudent_UserIdAndExam_ExamId(UUID studentId, UUID examId, Pageable pageable);

    /** Điểm trung bình các lượt thi đã nộp của một đề (cập nhật exams.average_score). */
    @Query("select coalesce(avg(a.score), 0) from ExamAttempt a "
            + "where a.exam.examId = :examId and a.status in :statuses")
    BigDecimal averageScoreByExamId(@Param("examId") UUID examId,
                                    @Param("statuses") Collection<AttemptStatus> statuses);

    /** Id các lượt thi đang làm nhưng đã quá hạn (kể cả thời gian ân hạn) - dùng cho job tự nộp bài. */
    @Query("select a.attemptId from ExamAttempt a where a.status = :status and a.expiresAt < :cutoff "
            + "order by a.expiresAt asc")
    List<UUID> findOverdueAttemptIds(@Param("status") AttemptStatus status,
                                     @Param("cutoff") LocalDateTime cutoff,
                                     Pageable pageable);

    // ------------------------------------------------------------------
    // Hàng đợi chấm tay (Writing / Speaking): lượt thi đã nộp còn ít nhất một câu essay có nội dung
    // mà chưa được chấm điểm.
    // ------------------------------------------------------------------

    @EntityGraph(attributePaths = {"exam", "student"})
    @Query(value = "select a from ExamAttempt a where a.status in :statuses and exists ("
            + "select 1 from ExamAnswer d where d.attempt = a and d.question.questionType = "
            + ":essay "
            + "and d.manualScore is null and d.answerText is not null and length(trim(d.answerText)) > 0) "
            + "order by a.endTime asc",
            countQuery = "select count(a) from ExamAttempt a where a.status in :statuses and exists ("
                    + "select 1 from ExamAnswer d where d.attempt = a and d.question.questionType = "
                    + ":essay "
                    + "and d.manualScore is null and d.answerText is not null and length(trim(d.answerText)) > 0)")
    Page<ExamAttempt> findPendingManualGrading(@Param("statuses") Collection<AttemptStatus> statuses,
                                               @Param("essay") QuestionType essay,
                                               Pageable pageable);

    /** Như trên nhưng chỉ các đề thuộc khóa học do mentor này phụ trách. */
    @EntityGraph(attributePaths = {"exam", "student"})
    @Query(value = "select a from ExamAttempt a where a.status in :statuses "
            + "and a.exam.course.mentor.userId = :mentorId and exists ("
            + "select 1 from ExamAnswer d where d.attempt = a and d.question.questionType = "
            + ":essay "
            + "and d.manualScore is null and d.answerText is not null and length(trim(d.answerText)) > 0) "
            + "order by a.endTime asc",
            countQuery = "select count(a) from ExamAttempt a where a.status in :statuses "
                    + "and a.exam.course.mentor.userId = :mentorId and exists ("
                    + "select 1 from ExamAnswer d where d.attempt = a and d.question.questionType = "
                    + ":essay "
                    + "and d.manualScore is null and d.answerText is not null and length(trim(d.answerText)) > 0)")
    Page<ExamAttempt> findPendingManualGradingForMentor(@Param("statuses") Collection<AttemptStatus> statuses,
                                                        @Param("essay") QuestionType essay,
                                                        @Param("mentorId") UUID mentorId,
                                                        Pageable pageable);
}
