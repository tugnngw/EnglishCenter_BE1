package com.sba301.englishcenter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Bảng nối N-N giữa {@link VocabularySet} và {@link VocabularyWord}.
 * <p>
 * Một từ có thể nằm trong nhiều bộ; một bộ chứa nhiều từ. Bảng này giữ
 * luôn thứ tự hiển thị của từ trong bộ ({@code wordOrder}).
 */
@Entity
@Table(name = "vocabulary_set_words")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularySetWord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_id", nullable = false)
    private VocabularySet vocabularySet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false)
    private VocabularyWord word;

    /** Thứ tự hiển thị trong bộ. */
    @Builder.Default
    @Column(name = "word_order", nullable = false)
    private Integer wordOrder = 0;
}
