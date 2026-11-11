package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.HomeworkGraderType;
import com.sba301.englishcenter.common.enums.MentoringPlanStatus;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.entity.MentoringPlan;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.repository.MentoringPlanRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.service.HomeworkGraderAssignmentService;
import com.sba301.englishcenter.util.HomeworkContentData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai {@link HomeworkGraderAssignmentService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkGraderAssignmentServiceImpl implements HomeworkGraderAssignmentService {

    private final MentoringPlanRepository planRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final UserRepository userRepository;

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public boolean hasMentor(UUID studentId) {
        return findAssignedMentorId(studentId).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public String findAssignedMentorName(UUID studentId) {
        return findAssignedMentorId(studentId)
                .map(mentorId -> userRepository.findById(mentorId).orElse(null))
                .map(user -> user.getFullName())
                .orElse(null);
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
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
}
