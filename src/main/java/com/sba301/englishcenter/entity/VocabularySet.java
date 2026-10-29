package com.sba301.englishcenter.entity;

import com.sba301.englishcenter.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Một BỘ từ vựng trong KHO TỪ VỰNG CHUNG.
 * <p>
 * Bộ từ vựng được tạo độc lập — không gắn với khóa học nào lúc tạo.
 * Việc đưa bộ vào khóa học thực hiện qua {@link CourseVocabularySet}.
 */
@Entity
@Table(name = "vocabulary_sets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularySet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "set_id")
    private UUID setId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Người tạo bộ trong kho chung. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    /** Các từ thuộc bộ (bảng nối {@code vocabulary_set_words}). */
    @OneToMany(mappedBy = "vocabularySet", fetch = FetchType.LAZY)
    @Builder.Default
    private List<VocabularySetWord> words = new ArrayList<>();
}
