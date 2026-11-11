package com.sba301.englishcenter.common.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Trạng thái lượt thi - ánh xạ trực tiếp với PostgreSQL native enum {@code attempt_status_enum}.
 * <p>
 * Hằng số Java viết chữ thường đúng như nhãn trong DB (xem {@code CourseStatus}); các alias VIẾT HOA
 * chỉ để code dễ đọc.
 */
@Getter
@RequiredArgsConstructor
public enum AttemptStatus {

    in_progress("in_progress"),
    submitted("submitted"),
    graded("graded"),
    expired("expired"),
    /**
     * V30: học viên đã nộp đề cuối kỳ nhưng CHƯA mua gói mentor 1-1.
     * <p>
     * Phần trắc nghiệm đã được chấm tự động và trả điểm ngay. Phần tự luận
     * (Writing/Speaking) được lưu nháp, chờ học viên nâng cấp gói mentor để
     * mentor chấm tay — không cần làm lại bài.
     */
    awaiting_mentor("awaiting_mentor");

    public static final AttemptStatus IN_PROGRESS = in_progress;
    public static final AttemptStatus SUBMITTED = submitted;
    public static final AttemptStatus GRADED = graded;
    public static final AttemptStatus EXPIRED = expired;
    public static final AttemptStatus AWAITING_MENTOR = awaiting_mentor;

    @JsonValue
    private final String value;

    @JsonCreator
    public static AttemptStatus fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim().toLowerCase();
        for (AttemptStatus item : values()) {
            if (item.name().equals(clean) || item.value.equals(clean)) {
                return item;
            }
        }
        throw new IllegalArgumentException("Unknown AttemptStatus: " + value);
    }
}
