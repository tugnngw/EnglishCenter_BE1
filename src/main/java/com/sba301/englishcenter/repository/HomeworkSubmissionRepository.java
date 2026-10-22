package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.HomeworkSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HomeworkSubmissionRepository extends JpaRepository<HomeworkSubmission, UUID> {

    List<HomeworkSubmission> findByStudent_UserIdOrderBySubmittedDateDesc(UUID studentId);

    Optional<HomeworkSubmission> findByHomeworkContent_ContentIdAndStudent_UserId(UUID contentId, UUID studentId);
}