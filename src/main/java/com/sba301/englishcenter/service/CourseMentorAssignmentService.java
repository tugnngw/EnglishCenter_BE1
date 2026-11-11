package com.sba301.englishcenter.service;

import java.util.UUID;

/**
 * Gán mentor 1-1 của học viên vào khóa học khi học viên mua gói kèm 1-1.
 * <p>
 * Quy tắc đã chốt: khóa học tạo ra KHÔNG có mentor sẵn. Học viên mua gói mentor 1-1
 * thì mentor đó được thêm vào các khóa học mà học viên đang học → mentor đó chấm
 * bài tập của học viên trong khóa học đó.
 * <p>
 * Nếu khóa học ĐÃ có mentor (admin gán), không ghi đè — gói 1-1 vẫn có hiệu lực
 * riêng (xem {@code homework_submissions.assigned_grader_id}).
 */
public interface CourseMentorAssignmentService {

    /**
     * Gán mentor 1-1 của học viên vào các khóa học học viên đang học.
     * <p>
     * Chỉ gán cho khóa học CHƯA có mentor. Khóa học đã có mentor (do admin gán)
     * thì giữ nguyên.
     *
     * @param studentId học viên vừa thanh toán gói 1-1
     * @param mentorId  mentor 1-1 của học viên
     * @return số khóa học đã được gán mentor
     */
    int assignMentorToStudentCourses(UUID studentId, UUID mentorId);
}
