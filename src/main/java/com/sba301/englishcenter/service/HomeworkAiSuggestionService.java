package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.HomeworkAiSuggestionResponse;

import java.util.UUID;

/**
 * Gợi ý chấm từ AI cho MENTOR (nút "Xem gợi ý chấm từ AI").
 * <p>
 * <b>Khác với {@link HomeworkAiGradingService}:</b> ở đây AI KHÔNG ghi điểm vào
 * hệ thống — AI chỉ chấm thử để mentor tham khảo, mentor vẫn là người quyết
 * định điểm chính thức. Vì vậy không đụng vào {@code score} / {@code passed} /
 * {@code graded_date} của bài nộp.
 * <p>
 * Áp dụng cho bài THỦ CÔNG (text / file / audio) của học viên đã mua gói mentor.
 */
public interface HomeworkAiSuggestionService {

    /**
     * Trả về gợi ý chấm từ AI cho một bài nộp (KHÔNG ghi điểm vào hệ thống).
     *
     * @param submissionId bài nộp cần gợi ý
     * @return gợi ý (điểm, nhận xét, điểm yếu, gợi ý sửa) hoặc null nếu bài không có nội dung
     */
    HomeworkAiSuggestionResponse suggest(UUID submissionId);
}
