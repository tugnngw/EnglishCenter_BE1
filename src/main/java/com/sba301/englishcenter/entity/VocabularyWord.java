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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Một từ vựng trong KHO TỪ VỰNG CHUNG.
 * <p>
 * Từ vựng KHÔNG thuộc về khóa học nào — nó được tạo độc lập trong
 * {@link VocabularySet} và chỉ được "gán" vào khóa học qua
 * {@link CourseVocabularySet}. Nhờ vậy cùng một từ có thể dùng cho nhiều
 * khóa học mà không phải nhân bản dữ liệu.
 */
@Entity
@Table(name = "vocabulary_words")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyWord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "word_id")
    private UUID wordId;

    /** Từ tiếng Anh (bắt buộc). Unique case-insensitive ở tầng DB. */
    @Column(name = "word", nullable = false)
    private String word;

    /** Phiên âm IPA. */
    @Column(name = "ipa")
    private String ipa;

    /** Nghĩa tiếng Việt. */
    @Column(name = "meaning")
    private String meaning;

    /** Đường dẫn file audio phát âm. */
    @Column(name = "audio_url")
    private String audioUrl;

    /** Từ loại (noun / verb / adjective...). */
    @Column(name = "part_of_speech")
    private String partOfSpeech;

    /** Câu ví dụ. */
    @Column(name = "example", columnDefinition = "TEXT")
    private String example;

    /** Người tạo từ trong kho chung. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;
}
