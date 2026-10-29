package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.CourseVocabularySet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseVocabularySetRepository extends JpaRepository<CourseVocabularySet, UUID> {

    /** Các bộ từ vựng đã gán vào 1 khóa học (kèm bộ + từ để dựng response). */
    @Query("""
            SELECT DISTINCT cvs FROM CourseVocabularySet cvs
            JOIN FETCH cvs.vocabularySet s
            LEFT JOIN FETCH s.words sw
            LEFT JOIN FETCH sw.word
            WHERE cvs.course.courseId = :courseId
            ORDER BY cvs.displayOrder ASC
            """)
    List<CourseVocabularySet> findByCourse_CourseIdWithDetails(@Param("courseId") UUID courseId);

    /** Danh sách id bộ đã gán vào khóa học (dùng để tick sẵn trong UI). */
    @Query("SELECT cvs.vocabularySet.setId FROM CourseVocabularySet cvs WHERE cvs.course.courseId = :courseId")
    List<UUID> findSetIdsByCourseId(@Param("courseId") UUID courseId);

    boolean existsByCourse_CourseIdAndVocabularySet_SetId(UUID courseId, UUID setId);

    /** Lấy 1 liên kết cụ thể (khóa học + bộ) — dùng cho thao tác gỡ. */
    @Query("""
            SELECT cvs FROM CourseVocabularySet cvs
            JOIN FETCH cvs.vocabularySet s
            WHERE cvs.course.courseId = :courseId
              AND s.setId = :setId
            """)
    Optional<CourseVocabularySet> findLink(@Param("courseId") UUID courseId,
                                          @Param("setId") UUID setId);

    long countByCourse_CourseId(UUID courseId);

    /** Xoá toàn bộ liên kết của 1 khóa học (dùng khi lưu danh sách mới). */
    @Modifying
    @Query("DELETE FROM CourseVocabularySet cvs WHERE cvs.course.courseId = :courseId")
    void deleteByCourseId(@Param("courseId") UUID courseId);

    /**
     * Xoá toàn bộ liên kết của 1 bộ từ vựng (dùng khi xoá bộ khỏi kho chung).
     *
     * Tránh `findAll()` + filter trong Java (O(n) + N+1).
     */
    @Modifying
    @Query("DELETE FROM CourseVocabularySet cvs WHERE cvs.vocabularySet.setId = :setId")
    void deleteByVocabularySetId(@Param("setId") UUID setId);

    /**
     * Đếm số khóa học đang dùng từng bộ — 1 query cho TOÀN BỘ kho chung.
     *
     * @return map {@code setId} → số khóa học dùng bộ đó
     */
    @Query("""
            SELECT cvs.vocabularySet.setId, COUNT(cvs)
            FROM CourseVocabularySet cvs
            GROUP BY cvs.vocabularySet.setId
            """)
    List<Object[]> countUsagePerSet();

    /** Số khóa học đang dùng 1 bộ cụ thể. */
    @Query("""
            SELECT COUNT(cvs)
            FROM CourseVocabularySet cvs
            WHERE cvs.vocabularySet.setId = :setId
            """)
    long countUsageOfSet(@Param("setId") UUID setId);
}
