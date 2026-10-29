package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.VocabularySetWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VocabularySetWordRepository extends JpaRepository<VocabularySetWord, UUID> {

    /** Các liên kết bộ ↔ từ của 1 bộ (thứ tự hiển thị). */
    List<VocabularySetWord> findByVocabularySet_SetIdOrderByWordOrderAsc(UUID setId);

    /** Xoá toàn bộ liên kết của 1 bộ (dùng khi cập nhật danh sách từ). */
    @Modifying
    @Query("DELETE FROM VocabularySetWord sw WHERE sw.vocabularySet.setId = :setId")
    void deleteBySetId(@Param("setId") UUID setId);
}
