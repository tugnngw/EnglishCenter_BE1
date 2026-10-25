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
public class LearningPathResponse {
    private UUID learningPathId;
    private UUID certificateId;
    private String name;
    private String description;
    private int totalItems;
    private int enrolledCount;
    private List<LearningPathItemResponse> items;
    private Instant createdAt;
    private Instant lastUpdated;
}
