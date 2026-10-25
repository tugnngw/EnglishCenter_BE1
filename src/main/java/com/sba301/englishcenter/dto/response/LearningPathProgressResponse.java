package com.sba301.englishcenter.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningPathProgressResponse {
    private UUID learningPathId;
    private UUID certificateId;
    private String name;
    private String status;
    private int progressPercent;
    private int completedItems;
    private int totalItems;
    private Instant enrolledAt;
    private Instant completedAt;
    private List<LearningPathItemResponse> items;
}
