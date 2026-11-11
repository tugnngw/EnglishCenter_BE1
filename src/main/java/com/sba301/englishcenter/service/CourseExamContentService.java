package com.sba301.englishcenter.service;

import com.sba301.englishcenter.entity.Exam;

import java.util.List;
import java.util.UUID;

/**
 * Quy tắc mở khoá nội dung <b>Bài thi cuối kỳ</b> ({@code content_type = 'exam'}) — V29.
 * <p>
 * Luật nghiệp vụ (chốt):
 * <ol>
 *   <li>Bài thi cuối kỳ <b>KHOÁ</b> cho tới khi học viên đã xem xong <b>TẤT CẢ</b> bài học
 *       (video) của khóa học.</li>
 *   <li>Đề chỉ có câu <b>trắc nghiệm / tự động chấm</b> → học viên làm được ngay.</li>
 *   <li>Đề có câu <b>tự luận</b> (Writing / Speaking — loại {@code essay}) → học viên phải
 *       <b>mở khoá mentor</b> (đã mua gói kèm 1-1) để mentor chấm tay.</li>
 * </ol>
 */
public interface CourseExamContentService {

    /**
     * Số bài học (video) của khóa học — dùng làm mẫu số của điều kiện "đã học hết".
     * <p>
     * Chỉ tính bài học CÓ VIDEO ({@code content_data.videoUrl} không rỗng): bài học
     * không có video thì học viên không thể xem → không thể hoàn thành → nếu tính vào
     * mẫu số thì bài thi cuối kỳ bị khoá vĩnh viễn.
     */
    long countLessons(UUID courseId);

    /** Id các bài học có video của khóa học. */
    List<UUID> lessonIdsWithVideo(UUID courseId);

    /**
     * Học viên đã xem xong TẤT CẢ video của khóa chưa?
     * <p>
     * Khóa chưa có bài học video nào → coi như đã xong (không chặn vô ích).
     */
    boolean allLessonsCompleted(UUID studentId, UUID courseId);

    /**
     * Đề thi này có câu tự luận (cần mentor chấm tay) hay không?
     * <p>
     * V30: dùng để gợi ý "nâng cấp gói mentor" cho học viên — KHÔNG dùng để khoá đề.
     */
    boolean requiresMentorGrading(UUID examId);

    /**
     * Học viên đã mua gói kèm 1-1 (có mentor chấm tay) hay chưa.
     * <p>
     * V30: chỉ dùng để QUYẾT ĐỊNH ai chấm phần tự luận sau khi nộp — KHÔNG dùng để
     * khoá đề thi nữa (học viên chưa mua gói vẫn làm + nộp được toàn bộ đề).
     */
    boolean hasMentorPlan(UUID studentId);

    /**
     * Đề thi có được gắn vào khóa học (dưới dạng nội dung 'exam') không?
     * <p>
     * Nếu có → luật mở khoá của nội dung 'exam' áp dụng cho đề đó, kể cả khi học viên
     * mở đề từ Ngân hàng đề (để không lách luật).
     */
    UUID findCourseIdOfExamContent(UUID examId);

    /** Đề có gắn vào nội dung 'exam' của khóa nào không. */
    boolean isExamContent(Exam exam);

    /**
     * Lý do học viên CHƯA được làm đề cuối kỳ (null = được phép).
     * <p>
     * Chỉ áp dụng cho đề đã được gắn vào khóa học dưới dạng nội dung 'exam'.
     * Kết quả:
     * <ul>
     *   <li>{@code "lessons"} — chưa xem xong TẤT CẢ video của khóa;</li>
     * </ul>
     * <p>
     * <b>V30:</b> KHÔNG còn lý do {@code "mentor"}. Học viên chưa mua gói mentor 1-1
     * vẫn làm và nộp được TOÀN BỘ đề cuối kỳ; phần tự luận được lưu nháp ở trạng
     * thái {@code awaiting_mentor} cho tới khi học viên nâng cấp gói.
     */
    String findLockReason(UUID examId, UUID studentId);
}
