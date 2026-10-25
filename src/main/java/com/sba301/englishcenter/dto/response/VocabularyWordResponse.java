package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Một từ vựng đọc được từ {@code course_content.content_data} (JSONB).
 * <p>
 * Dùng cho endpoint "thư viện từ vựng" — Admin chọn từ đã tạo sẵn để gắn vào
 * một nội dung vocabulary mới.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabularyWordResponse {

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
