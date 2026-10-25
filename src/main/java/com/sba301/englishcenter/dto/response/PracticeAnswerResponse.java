package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Bài làm luyện tập của học viên; evaluationId / bandScore có khi status = EVALUATED.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeAnswerResponse {

    private UUID answerId;
    private UUID practiceExamId;
    private String examTitle;
    private String examType;
    private String status;
    private Integer wordCount;
    private String content;
    private Instant submittedAt;
    private UUID evaluationId;
    private BigDecimal bandScore;
}
