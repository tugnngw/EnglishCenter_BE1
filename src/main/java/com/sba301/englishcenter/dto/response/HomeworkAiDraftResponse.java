package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Bản nháp bài tập AI đọc từ PDF (V27) - chưa lưu, mentor chỉnh rồi gửi POST /mentor/homeworks.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkAiDraftResponse {

    private String title;
    private String instructions;
    /** text / file / audio */
    private String submissionType;
    private BigDecimal maxScore;
}
