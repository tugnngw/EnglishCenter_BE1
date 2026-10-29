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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dev 3 - Kết quả bài kiểm tra năng lực (đề thi thử loại placement_test) gần nhất đã nộp của học viên
 * theo một chứng chỉ. Chỉ trả tỷ lệ % từng phần: thang điểm (band, tổng điểm, CEFR...) do admin cấu hình
 * ở trang Chứng chỉ (Dev 2: scoring trong description + properties của certificate_parts), FE quy đổi.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProficiencyResponse {

    UUID certificateId;
    String certificateName;

    UUID examId;
    String examTitle;
    UUID attemptId;
    LocalDateTime takenAt;

    /** assessed: đã có điểm đầy đủ; pending_grading: còn phần Writing / Speaking chờ mentor chấm */
    String status;

    /** Tỷ lệ % cả bài */
    BigDecimal percentage;

    /** Điểm từng phần thi của đề, kèm phần thi của chứng chỉ (nếu section có gắn) */
    List<SectionScoreResponse> sections;
}
