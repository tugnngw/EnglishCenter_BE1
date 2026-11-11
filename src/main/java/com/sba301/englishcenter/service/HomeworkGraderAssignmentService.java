package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.HomeworkGraderType;
import com.sba301.englishcenter.common.enums.MentoringPlanStatus;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.entity.MentoringPlan;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.repository.MentoringPlanRepository;
import com.sba301.englishcenter.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkGraderAssignmentService {

    /**
     * Trạng thái gói kèm 1-1 coi là "đã mua" (mentor được quyền chấm bài cho học viên).
     * <p>
     * {@code active}: đã thanh toán, đang học. {@code completed}: đã học xong.
     * {@code pending_payment}: mentor mới tạo gói, học viên CHƯA thanh toán → chưa tính.
     * {@code cancelled}: đã hủy → KHÔNG được chấm.
     */
    private final MentoringPlanRepository planRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    /**
     * Mentor sẽ chấm bài cho học viên này (nếu học viên đã mua gói 1-1).
     *
     * @param studentId học viên
     * @return id mentor, hoặc {@link Optional#empty()} nếu học viên không mua mentor nào
     */
    @Transactional(readOnly = true)
    public Optional<UUID> findAssignedMentorId(UUID studentId) {
        if (studentId == null) {
            return Optional.empty();
        }
        // CHỈ gói ĐÃ THANH TOÁN mới tính là "học viên đã mua mentor".
        // `pending_payment` = mentor mới tạo gói, học viên CHƯA thanh toán → chưa phải mua.
        // (Gói chờ thanh toán sẽ được gán lại khi webhook SePay xác nhận — xem
        //  HomeworkGraderAssignmentService#backfillUngradedSubmissions.)
        return planRepository
                .findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(plan -> plan.getStatus() == MentoringPlanStatus.active
                        || plan.getStatus() == MentoringPlanStatus.completed)
                .map(MentoringPlan::getMentorId)
                .filter(mentorId -> mentorId != null)
                .findFirst();
    }

    /**
     * Học viên này đã mua mentor 1-1 (chưa cần biết ai) hay chưa.
     */
    @Transactional(readOnly = true)
    public boolean hasMentor(UUID studentId) {
        return findAssignedMentorId(studentId).isPresent();
    }

    /**
     * Tên hiển thị của mentor 1-1 của học viên (null = chưa mua gói / không tìm thấy user).
     */
    @Transactional(readOnly = true)
    public String findAssignedMentorName(UUID studentId) {
        return findAssignedMentorId(studentId)
                .map(mentorId -> userRepository.findById(mentorId).orElse(null))
                .map(user -> user.getFullName())
                .orElse(null);
    }

    /**
     * Ai chấm bài này: mentor (nếu học viên đã mua) hay AI.
     */
    @Transactional(readOnly = true)
    public HomeworkGraderType resolveGraderType(UUID studentId) {
        return hasMentor(studentId) ? HomeworkGraderType.mentor : HomeworkGraderType.ai;
    }

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
    @Transactional(readOnly = true)
    public UUID assignGrader(HomeworkSubmission submission, UUID studentId) {
        UUID mentorId = resolveGraderForHomework(submission, studentId);
        if (submission != null) {
            submission.setAssignedGraderId(mentorId);
            submission.setGradedByType(mentorId != null
                    ? HomeworkGraderType.mentor.name()
                    : HomeworkGraderType.ai.name());
        }
        return mentorId;
    }

    /**
     * Mentor sẽ chấm bài nộp này:
     * <ul>
     *   <li>Bài của mentor tạo → người tạo bài ({@code ownerMentorId});</li>
     *   <li>Bài của khóa học → mentor 1-1 của học viên (nếu đã mua gói).</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public UUID resolveGraderForHomework(HomeworkSubmission submission, UUID studentId) {
        if (submission != null && submission.getHomeworkContent() != null) {
            CourseContent homework = submission.getHomeworkContent();
            if (homework.getCourse() == null && homework.getOwnerMentorId() != null) {
                return homework.getOwnerMentorId();
            }
        }
        return findAssignedMentorId(studentId).orElse(null);
    }

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
    @Transactional
    public int backfillUngradedSubmissions(UUID studentId, UUID mentorId) {
        if (studentId == null || mentorId == null) {
            return 0;
        }
        List<HomeworkSubmission> pending = submissionRepository
                .findPendingByStudentIdWithoutGrader(studentId);
        int updated = 0;
        for (HomeworkSubmission submission : pending) {
            submission.setAssignedGraderId(mentorId);
            submission.setGradedByType(HomeworkGraderType.mentor.name());
            submissionRepository.save(submission);
            updated++;
        }
        if (updated > 0) {
            log.info("Đã gán mentor {} cho {} bài nộp chờ chấm của học viên {} "
                    + "(học viên mua gói sau khi nộp bài)",
                    mentorId, updated, studentId);
        }
        return updated;
    }

    /**
     * Mentor được giao chấm một bài nộp cụ thể (dùng khi mentor mở bài để chấm).
     * <p>
     * Ưu tiên {@code assigned_grader_id} (snapshot lúc nộp). Nếu bài cũ chưa có snapshot
     * → fallback về người tạo bài (nếu là bài riêng của mentor) hoặc mentor 1-1
     * hiện tại của học viên.
     */
    @Transactional(readOnly = true)
    public Optional<UUID> resolveGraderId(HomeworkSubmission submission) {
        if (submission == null) {
            return Optional.empty();
        }
        if (submission.getAssignedGraderId() != null) {
            return Optional.of(submission.getAssignedGraderId());
        }
        UUID studentId = submission.getStudent() != null
                ? submission.getStudent().getUserId()
                : null;
        return Optional.ofNullable(resolveGraderForHomework(submission, studentId));
    }

    /**
     * Bài này có được giao cho mentor chấm tay không (đã chấm xong thì vẫn true).
     */
    @Transactional(readOnly = true)
    public boolean isMentorGraded(HomeworkSubmission submission) {
        if (submission == null) {
            return false;
        }
        if (HomeworkGraderType.mentor.name().equalsIgnoreCase(submission.getGradedByType())) {
            return true;
        }
        return submission.getAssignedGraderId() != null;
    }

    /**
     * Bài này có được AI chấm không.
     */
    @Transactional(readOnly = true)
    public boolean isAiGraded(HomeworkSubmission submission) {
        return submission != null
                && HomeworkGraderType.ai.name().equalsIgnoreCase(submission.getGradedByType());
    }

    /**
     * Bài tập này có cần chấm tay không (bài trắc nghiệm thì không).
     */
    public boolean needsManualGrading(CourseContent homework) {
        if (homework == null) {
            return false;
        }
        String type = HomeworkContentData.readText(homework.getContentData(), "submissionType");
        return type == null || !"multiple_choice".equalsIgnoreCase(type.trim());
    }
}
