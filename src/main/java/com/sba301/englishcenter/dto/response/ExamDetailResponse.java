package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 * Chi tiết đề thi theo quyền truy cập:
 * - Admin: toàn bộ câu hỏi + đáp án đúng.
 * - Học viên trong lượt thi: câu hỏi KHÔNG kèm đáp án đúng.
 * - Còn lại (xem trước): chỉ cấu trúc section và số câu, không có nội dung câu hỏi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamDetailResponse {

    ExamSummaryResponse exam;

    /** Người xem hiện tại có được làm đề này không (null với khách chưa đăng nhập). */
    Boolean accessible;

    /** Lý do không được làm (khi accessible = false). */
    String accessMessage;

    /** Mã lỗi khi accessible = false (VD 5033 đang chờ chấm). */
    Integer accessCode;

    /** Chỉ admin: bộ đề của khóa chưa đủ nên luật thi nối tiếp bị bỏ qua. */
    List<String> warnings;

    /** true nếu response có chứa nội dung câu hỏi. */
    Boolean questionsIncluded;

    BigDecimal maxScore;

    List<ExamSectionResponse> sections;

    /** Câu hỏi không thuộc section nào. */
    List<ExamQuestionResponse> unsectionedQuestions;
}
