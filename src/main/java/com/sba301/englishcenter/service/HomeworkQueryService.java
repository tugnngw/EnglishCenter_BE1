package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Truy vấn bài nộp bài tập.
 * <p>
 * <ul>
 *     <li>Học viên: xem bài nộp CỦA MÌNH, và xem trạng thái các bài tập trong khóa đã đăng ký;</li>
 *     <li>Mentor: xem bài nộp thuộc khóa học MÌNH DẠY (admin thấy tất cả).</li>
 * </ul>
 */
public interface HomeworkQueryService {

    /**
     * Bài nộp của học viên đang đăng nhập (mới nhất trước).
     */
    List<HomeworkSubmissionResponse> getMySubmissions();

    /**
     * Bài nộp CỦA học viên đang đăng nhập cho một bài tập cụ thể.
     */
    List<HomeworkSubmissionResponse> getMySubmissionsForHomework(UUID homeworkContentId);

    /**
     * Hàng đợi chấm của mentor:
     * <ul>
     *   <li>Bài được GIAO cho mentor này ({@code assigned_grader_id}) — học viên đã mua
     *       gói mentor 1-1 của mentor này → mentor đó chấm;</li>
     *   <li>Bài CHƯA gán ai của khóa học mentor phụ trách, hoặc của học viên mentor
     *       đang dạy kèm 1-1 → bình thường AI chấm, nhưng nếu AI lỗi thì mentor
     *       chấm tay được (fallback);</li>
     *   <li>Bài do CHÍNH mentor này tạo (bài tập riêng của mentor, V27).</li>
     * </ul>
     * Admin KHÔNG chấm bài tập → không có nhánh admin ở đây.
     *
     * @param source lọc theo nguồn bài tập:
     *               {@code all} (mặc định) | {@code course} (bài mặc định của khóa
     *               do admin tạo) | {@code mentor} (bài mentor tự tạo và gửi học viên)
     */
    PageResponse<HomeworkSubmissionResponse> getMentorQueue(Pageable pageable, boolean pendingOnly,
                                                            String source);

    /**
     * Chi tiết bài nộp cho mentor (kèm lịch sử chấm + rubric chấm điểm).
     */
    HomeworkSubmissionResponse getForGrading(UUID submissionId);
}
