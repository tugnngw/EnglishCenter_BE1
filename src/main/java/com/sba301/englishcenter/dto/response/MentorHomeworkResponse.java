package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bài tập riêng của mentor (V27) cho trang Homework của mentor.
 * learningPaths: các lộ trình đang chứa bài; submissionCount > 0 thì không đổi hình thức nộp / điểm tối đa, không xóa.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorHomeworkResponse {

    private UUID contentId;
    private String title;
    private String instructions;
    private String submissionType;
    private BigDecimal maxScore;
    private BigDecimal passingScore;
    private Integer maxAttempts;
    private Integer dueDays;
    private List<String> allowedFileTypes;
    private String attachmentUrl;
    private String attachmentName;
    private String attachmentPublicId;
    private List<PathRef> learningPaths;
    private long submissionCount;
    private Instant createdAt;
    private Instant lastUpdated;

    /** Lộ trình đang chứa bài tập */
    public record PathRef(UUID learningPathId, String name) {
    }
}
