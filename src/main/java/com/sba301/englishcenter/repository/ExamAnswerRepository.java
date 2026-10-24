package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.ExamAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamAnswerRepository extends JpaRepository<ExamAnswer, UUID> {

    @Query("select d from ExamAnswer d join fetch d.question left join fetch d.selectedOption "
            + "where d.attempt.attemptId = :attemptId")
    List<ExamAnswer> findDetailedByAttemptId(@Param("attemptId") UUID attemptId);

    @Query("select d from ExamAnswer d join fetch d.question left join fetch d.selectedOption "
            + "where d.attempt.attemptId = :attemptId and d.question.questionId = :questionId")
    Optional<ExamAnswer> findByAttemptIdAndQuestionId(@Param("attemptId") UUID attemptId,
                                                      @Param("questionId") UUID questionId);

    /** Đã có học viên trả lời câu hỏi này chưa (khóa chỉnh sửa câu hỏi). */
    boolean existsByQuestion_QuestionId(UUID questionId);
}
