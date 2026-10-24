package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.ExamQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamQuestionRepository extends JpaRepository<ExamQuestion, UUID> {

    /** Toàn bộ câu hỏi của đề kèm câu hỏi gốc và section (đáp án được nạp theo batch). */
    @Query("select eq from ExamQuestion eq join fetch eq.question q left join fetch eq.section s "
            + "where eq.exam.examId = :examId order by eq.questionOrder asc, eq.id asc")
    List<ExamQuestion> findDetailedByExamId(@Param("examId") UUID examId);

    @Query("select eq from ExamQuestion eq join fetch eq.question q left join fetch eq.section s "
            + "where eq.exam.examId = :examId and q.questionId = :questionId")
    Optional<ExamQuestion> findDetailedByExamIdAndQuestionId(@Param("examId") UUID examId,
                                                             @Param("questionId") UUID questionId);

    boolean existsByExam_ExamIdAndQuestion_QuestionId(UUID examId, UUID questionId);

    long countByExam_ExamId(UUID examId);

    long countBySection_SectionId(UUID sectionId);

    /** Câu hỏi này còn được dùng trong đề nào khác không. */
    boolean existsByQuestion_QuestionIdAndExam_ExamIdNot(UUID questionId, UUID examId);

    @Query("select coalesce(max(eq.questionOrder), 0) from ExamQuestion eq where eq.exam.examId = :examId")
    int findMaxQuestionOrder(@Param("examId") UUID examId);

    /** Tổng điểm tối đa của đề. */
    @Query("select coalesce(sum(q.points), 0) from ExamQuestion eq join eq.question q where eq.exam.examId = :examId")
    BigDecimal sumPointsByExamId(@Param("examId") UUID examId);
}
