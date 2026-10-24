package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.Exam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ExamRepository extends JpaRepository<Exam, UUID>, JpaSpecificationExecutor<Exam> {

    boolean existsByExamCode(String examCode);

    boolean existsByExamCodeAndExamIdNot(String examCode, UUID examId);
}
