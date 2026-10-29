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
 * Dev 3 - Năng lực hiện tại của học viên theo một chứng chỉ, lấy từ bài kiểm tra năng lực
 * (đề thi thử loại placement_test) gần nhất đã nộp. Dev 5 dùng để xếp lộ trình / lịch học.
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
    /** ielts | toeic | vstep | raw */
    String scale;

    UUID examId;
    String examTitle;
    UUID attemptId;
    LocalDateTime takenAt;

    /** assessed: đã có điểm đầy đủ; pending_grading: còn phần Writing / Speaking chờ mentor chấm */
    String status;

    /** IELTS / VSTEP: điểm chung; TOEIC: tổng L + R; null khi còn chờ chấm */
    BigDecimal overall;
    BigDecimal overallMax;
    /** Mô tả năng lực: "Competent user", "Bậc 4 (B2)", "TOEIC 650" */
    String level;
    /** Trình độ CEFR tương ứng (nếu quy đổi được) */
    String cefr;

    List<SkillScore> skills;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @FieldDefaults(level = AccessLevel.PRIVATE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkillScore {
        /** LISTENING | READING | WRITING | SPEAKING | OTHER */
        String skill;
        String title;
        BigDecimal score;
        BigDecimal maxScore;
        BigDecimal percentage;
        Boolean pendingManualGrading;
        String cefr;
    }
}
