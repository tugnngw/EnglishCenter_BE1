package com.sba301.englishcenter.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Dev 3 - Nhờ AI đọc file đề (PDF / nội dung Word, text) để tạo đề.
 * Gửi một trong hai: {@code fileBase64} (PDF) hoặc {@code text} (FE đã trích từ .docx / .txt).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiImportRequest {

    public enum Target { EXAM, PRACTICE }

    /** EXAM: đề thi (Task 3); PRACTICE: đề luyện Writing / Speaking (AI Practice) */
    @NotNull(message = "Chọn loại đề cần tạo (EXAM / PRACTICE)")
    Target target;

    /** Tên chứng chỉ của đề, để AI hiểu ngữ cảnh (IELTS, TOEIC...) */
    @Size(max = 255)
    String certificateName;

    /** Tên các phần thi của chứng chỉ (Listening, Reading...) mà AI được dùng sau [SECTION] */
    List<@Size(max = 255) String> sections;

    @Size(max = 255)
    String fileName;

    /** Nội dung PDF mã hoá base64 */
    String fileBase64;

    /** Nội dung văn bản (đã trích từ Word / .txt) */
    @Size(max = 200_000, message = "Nội dung file quá dài")
    String text;
}
