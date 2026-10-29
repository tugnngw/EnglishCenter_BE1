package com.sba301.englishcenter.repository;

import com.sba301.englishcenter.entity.VocabularyWord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VocabularyWordRepository extends JpaRepository<VocabularyWord, UUID> {

    /**
     * Tìm từ theo chính tả (case-insensitive) — kho chung cho phép trùng lặp
     * về mặt hiển thị nhưng DB đã có unique index {@code lower(word)} nên
     * method này dùng để tái sử dụng từ đã có thay vì tạo bản sao.
     */
    @Query("SELECT w FROM VocabularyWord w WHERE lower(w.word) = lower(:word)")
    Optional<VocabularyWord> findByWordIgnoreCase(@Param("word") String word);

    /** Tìm nhiều từ theo danh sách chính tả (dùng khi import/gán hàng loạt). */
    @Query("SELECT w FROM VocabularyWord w WHERE lower(w.word) IN :words")
    List<VocabularyWord> findAllByWordIgnoreCaseIn(@Param("words") List<String> words);
}
