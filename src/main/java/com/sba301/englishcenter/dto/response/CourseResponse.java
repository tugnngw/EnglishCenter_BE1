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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CourseResponse {

    UUID courseId;
    String code;
    String title;
    String description;
    String level;
    BigDecimal price;
    BigDecimal targetScore;
    BigDecimal entryScore;
    String thumbnailUrl;
    String status;

    UUID certificateId;
    String certificateName;
    UUID certificatePartId;
    String certificatePartName;

    /**
     * Null nghĩa là khóa học không gắn mentor (khóa học VOD tự học độc lập).
     * <p>
     * Admin KHÔNG gán mentor khi tạo/sửa khóa học — cột này được hệ thống gán
     * tự động khi học viên mua gói kèm 1-1 (xem {@code CourseMentorAssignmentService}).
     */
    UUID mentorId;
    String mentorName;

    /**
     * Mentor 1-1 của HỌC VIÊN ĐANG XEM khóa học này (null = học viên chưa mua gói kèm 1-1).
     * <p>
     * Mỗi học viên có thể mua gói 1-1 với mentor KHÁC NHAU, nên tên mentor hiển thị
     * trên khóa phải tính theo người xem — dùng cột {@code mentor_id} của khóa sẽ
     * hiển thị sai mentor của học viên khác.
     */
    UUID viewerMentorId;
    String viewerMentorName;

    /**
     * Tab "Bài tập" của khóa có mở cho học viên đang xem hay không.
     * <p>
     * {@code true} khi học viên đã mua gói kèm 1-1 (có mentor chấm tay) hoặc
     * người xem là Admin/Mentor (cần xem để quản lý).
     * {@code false} → FE ẨN hoàn toàn tab "Bài tập".
     */
    Boolean homeworkEnabled;

    UUID createdByUserId;
    LocalDateTime createdAt;
    LocalDateTime lastUpdated;
}