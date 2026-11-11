package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.HomeworkExamStatsResponse;

import java.util.UUID;

/**
 * Thống kê kết quả làm bài của học viên cho bài tập TRẮC NGHIỆM (Nhóm A).
 * <p>
 * Bài trắc nghiệm được hệ thống chấm tự động → mentor KHÔNG thao tác chấm,
 * chỉ xem bảng thống kê để nắm điểm yếu chung của học viên.
 */
public interface HomeworkExamStatsService {

    /**
     * Bảng thống kê kết quả làm bài của một bài tập trắc nghiệm.
     *
     * @param homeworkContentId bài tập (course_content có content_type = homework)
     */
    HomeworkExamStatsResponse getStats(UUID homeworkContentId);
}
