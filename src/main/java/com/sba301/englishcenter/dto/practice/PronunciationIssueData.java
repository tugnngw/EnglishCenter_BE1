package com.sba301.englishcenter.dto.practice;

/**
 * 1 từ nghi phát âm sai trong bài Speaking. Speaking chấm trên bản ghi giọng nói (Web Speech API), nên AI suy ra
 * từ chỗ máy nhận dạng ra từ khác / vô nghĩa so với ngữ cảnh.
 *
 * @param word   từ học viên muốn nói (AI suy ra theo ngữ cảnh)
 * @param heardAs từ máy nhận dạng được trong bản ghi
 * @param tip    cách sửa ngắn gọn bằng tiếng Việt (VD "âm /θ/ đặt lưỡi giữa hai răng")
 */
public record PronunciationIssueData(String word, String heardAs, String tip) {
}
