package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

/**
 * Một từ vựng.
 * <p>
 * Dùng cho CẢ HAI nguồn dữ liệu:
 * <ul>
 *   <li>KHO TỪ VỰNG CHUNG ({@code vocabulary_words}) — từ thuộc kho chung và
 *       được dùng lại cho nhiều khóa học qua {@code course_vocabulary_sets};</li>
 *   <li>Nội dung {@code course_content.content_data} (JSONB) — dùng cho endpoint
 *       "thư viện từ vựng", nơi Admin chọn từ đã tạo sẵn để gắn vào một
 *       nội dung vocabulary mới.</li>
 * </ul>
 * Vì vậy {@link #wordId} chỉ có giá trị với từ đến từ kho chung; từ đọc từ
 * JSONB thì {@code wordId = null}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabularyWordResponse {

    /** Id của từ trong kho từ vựng chung. NULL với từ đọc từ JSONB. */
    UUID wordId;

    /** Từ tiếng Anh (bắt buộc). */
    String word;

    /** Phiên âm IPA. */
    String ipa;

    /** Alias của {@link #ipa} — màn hình client đọc key này. */
    String phonetic;

    /** Nghĩa tiếng Việt. */
    String meaning;

    /** Đường dẫn file audio phát âm. */
    String audioUrl;

    /** Từ loại (noun / verb / adjective...). */
    String partOfSpeech;

    /** Câu ví dụ. */
    String example;
}
