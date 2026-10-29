package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * Một từ vựng khi tạo / cập nhật bộ trong kHO TỪ VỰNG CHUNG.
 * <p>
 * {@code word} là khóa nhận diện ngữ nghĩa — nếu từ đã tồn tại trong kho
 * chung thì backend tái sử dụng bản ghi cũ (chỉ bổ sung field còn thiếu).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VocabularyWordRequest {

    @NotBlank(message = "Từ tiếng Anh không được để trống")
    @Size(max = 255, message = "Từ tiếng Anh không được vượt quá 255 ký tự")
    String word;

    /** Phiên âm IPA. */
    @Size(max = 100, message = "Phiên âm không được vượt quá 100 ký tự")
    String ipa;

    @Size(max = 500, message = "Nghĩa không được vượt quá 500 ký tự")
    String meaning;

    @Size(max = 500, message = "Link audio không được vượt quá 500 ký tự")
    String audioUrl;

    @Size(max = 100, message = "Từ loại không được vượt quá 100 ký tự")
    String partOfSpeech;

    String example;
}
