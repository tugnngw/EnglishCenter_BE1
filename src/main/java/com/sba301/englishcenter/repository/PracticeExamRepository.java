package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.PracticeExam;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PracticeExamRepository extends JpaRepository<PracticeExam, UUID>, JpaSpecificationExecutor<PracticeExam> {

    // Khóa dòng khi thêm bài làm / ghi kết quả chấm: answers là 1 mảng JSONB trên cùng dòng
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PracticeExam> findWithLockByPracticeExamId(UUID practiceExamId);

    // Tìm đề chứa bài làm / kết quả chấm theo id. Cần native query vì lọc trong mảng JSONB (toán tử @>);
    // tham số là chuỗi JSON, vd [{"answerId":"..."}] hoặc [{"studentId":"..."}]
    @Query(value = "select * from practice_exams where answers @> cast(:filter as jsonb)", nativeQuery = true)
    List<PracticeExam> findByAnswersContaining(@Param("filter") String answerFilterJson);
}
