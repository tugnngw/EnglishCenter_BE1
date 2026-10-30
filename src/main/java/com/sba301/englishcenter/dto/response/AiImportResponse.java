package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Dev 3 - Kết quả AI đọc file đề.
 * EXAM: {@code content} viết theo mẫu nhập đề ([SECTION], câu hỏi, *đáp án...) để FE kiểm tra, xem trước rồi tạo.
 * PRACTICE: danh sách đề luyện Writing / Speaking để admin xem trước rồi tạo.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiImportResponse {

    String content;

    List<PracticeDraft> prompts;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class PracticeDraft {
        String title;
        String description;
        /** WRITING | SPEAKING */
        String examType;
        String bankName;
    }
}
