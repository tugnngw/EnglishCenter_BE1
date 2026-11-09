package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.LearningPath;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LearningPathRepository extends JpaRepository<LearningPath, UUID> {

    Page<LearningPath> findByCertificateId(UUID certificateId, Pageable pageable);

    // Khóa dòng khi ghi danh / cập nhật tiến độ: student_progress là 1 mảng JSONB trên cùng dòng
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LearningPath> findWithLockByLearningPathId(UUID learningPathId);

    // Lộ trình học viên đã ghi danh. Cần native query vì lọc trong mảng JSONB (toán tử @>),
    // derived query / Specification không biểu diễn được. Tham số là chuỗi JSON [{"studentId":"..."}]
    @Query(value = "select * from learning_paths where student_progress @> cast(:filter as jsonb) order by created_at",
            nativeQuery = true)
    List<LearningPath> findEnrolledBy(@Param("filter") String studentFilterJson);

    // Lộ trình có chặng khớp bộ lọc trong mảng JSONB items (V27: tìm lộ trình chứa 1 bài tập của mentor).
    // Tham số là chuỗi JSON [{"type":"HOMEWORK","refId":"..."}]
    @Query(value = "select * from learning_paths where items @> cast(:filter as jsonb) order by created_at",
            nativeQuery = true)
    List<LearningPath> findContainingItem(@Param("filter") String itemFilterJson);
}
