package com.sba301.englishcenter.common.enums;

/**
 * Ai chấm bài tập của học viên.
 * <p>
 * Quy tắc nghiệp vụ:
 * <ul>
 *   <li>{@link #ai} — học viên KHÔNG mua mentor 1-1 → AI chấm tự động lúc nộp;</li>
 *   <li>{@link #mentor} — học viên ĐÃ mua gói mentor 1-1 → mentor đó chấm tay.</li>
 * </ul>
 * Bài trắc nghiệm LUÔN tự chấm (điểm lấy từ lượt thi) → không dùng enum này.
 */
public enum HomeworkGraderType {
    ai,
    mentor
}
