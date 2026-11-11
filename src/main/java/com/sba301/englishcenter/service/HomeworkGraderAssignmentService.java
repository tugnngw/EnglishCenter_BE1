package com.sba301.englishcenter.service;

import com.sba301.englishcenter.entity.HomeworkSubmission;

import java.util.Optional;
import java.util.UUID;

/**
 * Quyết định AI chấm hay mentor chấm một bài nộp bài tập.
 * <p>
 * <b>Quy tắc nghiệp vụ (đã chốt):</b>
 * <ul>
 *   <li>Học viên KHÔNG mua mentor 1-1 → <b>AI chấm tự động</b> (bài tự luận / nộp file /
 *       bài ghi âm). Bài trắc nghiệm vốn đã tự chấm sẵn từ lượt thi.</li>
 *   <li>Học viên ĐÃ mua gói mentor 1-1 (đã thanh toán) → <b>mentor đó chấm tay</b>.
 *       Bài trắc nghiệm vẫn tự chấm như bình thường.</li>
 *   <li>Admin KHÔNG tham gia chấm bài tập — chỉ tạo nội dung.</li>
 * </ul>
 * <p>
 * Mentor được giao chấm là <b>mentor 1-1 của chính học viên đó</b> (gói đã thanh toán),
 * KHÔNG phụ thuộc khóa học có gán mentor hay không — vì "mua mentor" là quyền của học viên.
 */
public interface HomeworkGraderAssignmentService {

    /**
     * Mentor sẽ chấm bài cho học viên này (nếu học viên đã mua gói 1-1).
     *
     * @param studentId học viên
     * @return id mentor, hoặc {@link Optional#empty()} nếu học viên không mua mentor nào
     */
    Optional<UUID> findAssignedMentorId(UUID studentId);

    /**
     * Học viên này đã mua mentor 1-1 (chưa cần biết ai) hay chưa.
     */
    boolean hasMentor(UUID studentId);

    /**
     * Tên hiển thị của mentor 1-1 của học viên (null = chưa mua gói / không tìm thấy user).
     */
    String findAssignedMentorName(UUID studentId);

    /**
     * Gán mentor chấm cho một bài nộp (snapshot lúc nộp).
     * <p>
     * Quy tắc:
     * <ul>
     *   <li>Bài tập của MENTOR tạo ({@code course_content.course_id = NULL}, có
     *       {@code owner_mentor_id}) → người tạo bài đó chấm, KHÔNG phải mentor
     *       1-1 của học viên (nếu khác người tạo);</li>
     *   <li>Bài tập thuộc KHÓA HỌC (admin tạo) → mentor 1-1 của học viên chấm
     *       (null nếu học viên chưa mua gói → AI chấm).</li>
     * </ul>
     *
     * @return id mentor được giao, hoặc null nếu không có → AI chấm
     */
    UUID assignGrader(HomeworkSubmission submission, UUID studentId);

    /**
     * Xác định mentor sẽ chấm bài nộp này (chưa ghi vào DB).
     */
    UUID resolveGraderForHomework(HomeworkSubmission submission, UUID studentId);

    /**
     * Gán lại mentor cho các bài nộp CHƯA CHẤM của học viên — dùng khi học viên
     * MUA GÓI SAU KHI ĐÃ NỘP BÀI.
     * <p>
     * Lúc nộp, học viên chưa mua gói → bài bị gán {@code assigned_grader_id = null}
     * và AI chấm. Khi học viên mua gói sau đó, các bài chưa chấm phải chuyển sang
     * mentor 1-1 để mentor thấy và chấm tay.
     * <p>
     * Bài ĐÃ CHẤM rồi thì KHÔNG đụng vào (AI đã chấm là giữ nguyên, tránh mất
     * lịch sử chấm).
     *
     * @param studentId học viên vừa mua gói
     * @param mentorId  mentor 1-1 của học viên
     * @return số bài đã chuyển sang cho mentor
     */
    int backfillUngradedSubmissions(UUID studentId, UUID mentorId);

    /**
     * Bài này có được giao cho mentor chấm tay không (đã chấm xong thì vẫn true).
     */
    boolean isMentorGraded(HomeworkSubmission submission);
}
