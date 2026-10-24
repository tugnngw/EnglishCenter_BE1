package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.ExamSection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamSectionRepository extends JpaRepository<ExamSection, UUID> {

    List<ExamSection> findByExam_ExamIdOrderBySectionOrderAscCreatedAtAsc(UUID examId);

    Optional<ExamSection> findBySectionIdAndExam_ExamId(UUID sectionId, UUID examId);

    long countByExam_ExamId(UUID examId);
}
