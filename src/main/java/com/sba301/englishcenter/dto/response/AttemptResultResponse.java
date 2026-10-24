package com.sba301.englishcenter.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.List;

/**
 * Kết quả lượt thi: điểm tổng, điểm từng section, đáp án và nhận xét.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AttemptResultResponse {

    AttemptSummaryResponse attempt;

    Integer correctCount;
    Integer incorrectCount;
    Integer unansweredCount;

    /** Đề có cho xem điểm không. */
    Boolean scoreVisible;

    /** Đề có cho xem đáp án đúng / giải thích không. */
    Boolean answersVisible;

    List<SectionScoreResponse> sectionScores;
    List<AnswerResultResponse> answers;
}
