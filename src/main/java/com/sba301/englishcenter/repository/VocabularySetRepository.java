package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.VocabularySet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VocabularySetRepository extends JpaRepository<VocabularySet, UUID> {

    /**
     * Toàn bộ bộ trong KHO TỪ VỰNG CHUNG, kèm sẵn danh sách từ để tránh
     * N+1 khi dựng response.
     */
    @Query("""
            SELECT DISTINCT s FROM VocabularySet s
            LEFT JOIN FETCH s.words sw
            LEFT JOIN FETCH sw.word
            ORDER BY s.createdAt DESC
            """)
    List<VocabularySet> findAllWithWords();

    /** Bộ cụ thể kèm danh sách từ. */
    @Query("""
            SELECT DISTINCT s FROM VocabularySet s
            LEFT JOIN FETCH s.words sw
            LEFT JOIN FETCH sw.word
            WHERE s.setId = :setId
            """)
    Optional<VocabularySet> findByIdWithWords(@Param("setId") UUID setId);

    /** Tìm bộ theo tiêu đề (case-insensitive) — chặn trùng trong kho chung. */
    @Query("SELECT s FROM VocabularySet s WHERE lower(s.title) = lower(:title)")
    Optional<VocabularySet> findByTitleIgnoreCase(@Param("title") String title);
}
