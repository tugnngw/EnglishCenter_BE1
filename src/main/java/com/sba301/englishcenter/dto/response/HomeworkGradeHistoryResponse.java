package com.sba301.englishcenter.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một dòng lịch sử chấm / sửa điểm của bài nộp bài tập.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class HomeworkGradeHistoryResponse {

    UUID historyId;

    /** Người thực hiện chấm. */
    UUID gradedByUserId;
    String gradedByName;

    /** Điểm trước khi sửa (null ở lần chấm đầu). */
    BigDecimal oldScore;

    BigDecimal newScore;

    Boolean passed;

    String feedback;

    LocalDateTime gradedAt;
}
