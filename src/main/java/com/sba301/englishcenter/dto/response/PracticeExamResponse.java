package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Đề luyện tập (không kèm bài làm của học viên).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeExamResponse {

    private UUID practiceExamId;
    private String title;
    private String description;
    private String examType;
    private String bankName;
    private UUID courseId;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant lastUpdated;
}
