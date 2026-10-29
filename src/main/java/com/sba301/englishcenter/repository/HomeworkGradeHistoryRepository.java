package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.HomeworkGradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HomeworkGradeHistoryRepository extends JpaRepository<HomeworkGradeHistory, UUID> {

    /** Lịch sử chấm của một bài nộp — mới nhất trước. */
    List<HomeworkGradeHistory> findBySubmission_SubmissionIdOrderByGradedAtDesc(UUID submissionId);
}
