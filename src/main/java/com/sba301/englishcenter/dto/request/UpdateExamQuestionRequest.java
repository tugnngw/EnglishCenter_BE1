package com.sba301.englishcenter.dto.request;

import com.sba301.englishcenter.common.enums.DifficultyLevel;
import com.sba301.englishcenter.common.enums.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Sửa câu hỏi trong đề. Trường null = giữ nguyên; options != null sẽ THAY THẾ toàn bộ đáp án.
 * removeFromSection = true để đưa câu hỏi ra khỏi section hiện tại.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateExamQuestionRequest {

    QuestionType questionType;

    Boolean removeFromSection;

    @Size(min = 1, message = "Nội dung câu hỏi không được để trống")
    String questionText;

    @Positive(message = "Điểm của câu hỏi phải lớn hơn 0")
    BigDecimal points;

    DifficultyLevel difficultyLevel;

    @Size(max = 255, message = "Chủ đề không được vượt quá 255 ký tự")
    String topic;

    /** Giải thích đáp án, hiển thị khi xem kết quả (nếu đề cho phép). */
    String explanation;

    @Size(max = 500, message = "URL audio không được vượt quá 500 ký tự")
    String audioUrl;

    @Size(max = 500, message = "URL ảnh không được vượt quá 500 ký tự")
    String imageUrl;

    UUID certificatePartId;

    @Valid
    List<QuestionOptionRequest> options;

    /** Section chứa câu hỏi (null = không thuộc section). */
    UUID sectionId;

    /** Thứ tự trong đề; null = thêm vào cuối. */
    @Min(value = 1, message = "Thứ tự câu hỏi phải lớn hơn 0")
    Integer questionOrder;

    @Min(value = 1, message = "Thời gian gợi ý phải lớn hơn 0 giây")
    Integer timeLimitSeconds;
}
