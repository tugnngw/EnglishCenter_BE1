package com.sba301.englishcenter.dto.practice;

import java.math.BigDecimal;

/**
 * Điểm + nhận xét của 1 tiêu chí chấm (band 0 - 9, bước 0.5).
 */
public record CriterionScoreData(BigDecimal band, String comment) {
}
