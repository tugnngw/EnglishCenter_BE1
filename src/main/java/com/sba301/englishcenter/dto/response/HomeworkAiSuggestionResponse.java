package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

/**
 * Gợi ý chấm từ AI cho mentor (nút "Xem gợi ý chấm từ AI").
 * <p>
 * AI không chấm hộ mentor — chỉ gợi ý để mentor chấm nhanh hơn. Mentor vẫn
 * là người quyết định điểm chính thức.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HomeworkAiSuggestionResponse {

    /** Điểm AI gợi ý (thang của bài tập). */
    BigDecimal suggestedScore;

    /** Thang điểm AI dùng để chấm (VD "IELTS", "TOEIC"). */
    String scaleLabel;

    /** Nhận xét của AI. */
    String feedback;

    /** Điểm yếu AI nhận ra. */
    List<String> weaknesses;

    /** Gợi ý sửa của AI. */
    List<String> suggestions;

    /** CEFR level (nếu có). */
    String cefrLevel;
}
