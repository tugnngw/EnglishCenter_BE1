package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.dto.response.StudentProgressResponse;
import com.sba301.englishcenter.dto.response.StudentProgressResponse.CourseProgress;
import com.sba301.englishcenter.dto.response.StudentProgressResponse.HomeworkScore;
import com.sba301.englishcenter.dto.response.StudentProgressResponse.SessionSummary;
import com.sba301.englishcenter.entity.Booking;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.Enrollment;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.BookingRepository;
import com.sba301.englishcenter.repository.ConsultationRequestRepository;
import com.sba301.englishcenter.repository.EnrollmentRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.repository.MentoringPlanRepository;
import com.sba301.englishcenter.service.ConsultationService;
import com.sba301.englishcenter.service.CourseExamContentService;
import com.sba301.englishcenter.util.HomeworkContentData;
import com.sba301.englishcenter.service.LearningPathService;
import com.sba301.englishcenter.service.LessonProgressService;
import com.sba301.englishcenter.service.StudentProgressService;
import com.sba301.englishcenter.util.UtcTimeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StudentProgressServiceImpl implements StudentProgressService {

    private final ConsultationService consultationService;
    private final LearningPathService learningPathService;
    private final CourseExamContentService courseExamContentService;
    private final LessonProgressService lessonProgressService;
    private final ConsultationRequestRepository consultationRequestRepository;
    private final MentoringPlanRepository mentoringPlanRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final HomeworkSubmissionRepository homeworkSubmissionRepository;
    private final BookingRepository bookingRepository;

    @Override
    // Không readOnly: tiến độ lộ trình được tính lại và lưu (LearningPathService.getMyProgress)
    @Transactional
    public StudentProgressResponse getProgress(UUID mentorId, UUID studentId) {
        // Không phải người học -> USER_NOT_FOUND
        consultationService.getStudent(studentId);
        boolean inCharge = consultationRequestRepository.existsByMentorIdAndStudentId(mentorId, studentId)
                || mentoringPlanRepository.existsByMentorIdAndStudentId(mentorId, studentId);
        if (!inCharge) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return StudentProgressResponse.builder()
                .courses(courseProgress(studentId))
                .homeworks(homeworkScores(studentId))
                .learningPaths(learningPathService.getMyProgress(studentId))
                .sessions(sessionSummary(mentorId, studentId))
                .build();
    }

    // ===================== helpers =====================

    /** Khóa đang học: % = bài học có video đã xem xong / tổng bài học có video (giống luật mở bài thi cuối kỳ) */
    private List<CourseProgress> courseProgress(UUID studentId) {
        List<CourseProgress> result = new ArrayList<>();
        for (Enrollment enrollment : enrollmentRepository.findByStudentIdAndStatus(studentId, Enrollment.Status.active)) {
            UUID courseId = enrollment.getCourse().getCourseId();
            List<UUID> lessonIds = courseExamContentService.lessonIdsWithVideo(courseId);
            int total = lessonIds.size();
            int completed = (int) lessonProgressService.countCompletedLessons(studentId, courseId, lessonIds);
            result.add(CourseProgress.builder()
                    .courseId(courseId)
                    .courseTitle(enrollment.getCourse().getTitle())
                    .thumbnailUrl(enrollment.getCourse().getThumbnailUrl())
                    .completedLessons(completed)
                    .totalLessons(total)
                    .progressPercent(total == 0 ? 0 : Math.round(completed * 100f / total))
                    .build());
        }
        return result;
    }

    /** Gộp các lần nộp theo bài tập (danh sách đã sắp mới nhất trước), lấy điểm của lần chấm gần nhất */
    private List<HomeworkScore> homeworkScores(UUID studentId) {
        Map<UUID, List<HomeworkSubmission>> byHomework = new LinkedHashMap<>();
        for (HomeworkSubmission submission : homeworkSubmissionRepository.findByStudent_UserIdOrderBySubmittedDateDesc(studentId)) {
            byHomework.computeIfAbsent(submission.getHomeworkContent().getContentId(), id -> new ArrayList<>())
                    .add(submission);
        }

        List<HomeworkScore> result = new ArrayList<>();
        for (List<HomeworkSubmission> submissions : byHomework.values()) {
            HomeworkSubmission latest = submissions.get(0);
            HomeworkSubmission latestGraded = submissions.stream()
                    .filter(s -> s.getGradedDate() != null)
                    .findFirst()
                    .orElse(null);
            CourseContent homework = latest.getHomeworkContent();
            result.add(HomeworkScore.builder()
                    .homeworkId(homework.getContentId())
                    .title(homework.getTitle())
                    .courseTitle(homework.getCourse() != null ? homework.getCourse().getTitle() : null)
                    .score(latestGraded != null ? latestGraded.getScore() : null)
                    .maxScore(positive(HomeworkContentData.readNumber(homework.getContentData(), "maxScore")))
                    .passingScore(positive(HomeworkContentData.readNumber(homework.getContentData(), "passingScore")))
                    .passed(latestGraded != null ? latestGraded.getPassed() : null)
                    .pendingGrading(latest.getGradedDate() == null)
                    .attemptCount(submissions.size())
                    .lastSubmittedDate(latest.getSubmittedDate())
                    .gradedDate(latestGraded != null ? latestGraded.getGradedDate() : null)
                    .build());
        }
        return result;
    }

    /** Buổi kèm 1-1 giữa học viên và mentor đang xem (không tính buổi chờ thanh toán) */
    private SessionSummary sessionSummary(UUID mentorId, UUID studentId) {
        LocalDateTime now = UtcTimeUtil.nowUtc();
        int doneCount = 0;
        int noShowCount = 0;
        int cancelledCount = 0;
        int upcomingCount = 0;
        for (Booking booking : bookingRepository.findByMentorIdAndStudentId(mentorId, studentId)) {
            switch (booking.getStatus()) {
                case completed -> doneCount++;
                case no_show -> noShowCount++;
                case cancelled -> cancelledCount++;
                case confirmed -> {
                    if (booking.getSchedule().getEndTime().isAfter(now)) {
                        upcomingCount++;
                    }
                }
                default -> {
                    // pending = chờ thanh toán gói: chưa tính
                }
            }
        }
        return SessionSummary.builder()
                .total(doneCount + noShowCount + cancelledCount + upcomingCount)
                .completed(doneCount)
                .noShow(noShowCount)
                .cancelled(cancelledCount)
                .upcoming(upcomingCount)
                .build();
    }

    private static BigDecimal positive(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : null;
    }
}
