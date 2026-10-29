package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.dto.request.GradeHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.response.HomeworkGradeHistoryResponse;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.HomeworkGradeHistory;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.HomeworkGradeHistoryRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import com.sba301.englishcenter.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chấm điểm bài tập cho mentor/admin (bài tự luận + bài nộp file).
 * <p>
 * Trách nhiệm:
 * <ul>
 *     <li>Phân quyền: admin chấm mọi bài; mentor chỉ chấm bài thuộc khóa học mình phụ trách;</li>
 *     <li>Tính lại {@code passed} theo điểm đạt của bài tập;</li>
 *     <li>Ghi lịch sử mỗi lần chấm/sửa điểm (người sửa, điểm cũ, điểm mới, thời gian);</li>
 *     <li>Giới hạn điểm theo điểm tối đa của bài tập (nếu có).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkGradingService {

    private final HomeworkSubmissionRepository submissionRepository;
    private final HomeworkGradeHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinary;

    /**
     * Mentor chấm / sửa điểm một bài nộp.
     */
    @Transactional
    public HomeworkSubmissionResponse grade(UUID submissionId, GradeHomeworkSubmissionRequest request) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND));

        CourseContent homework = submission.getHomeworkContent();
        if (homework == null || homework.getContentType() != ContentType.HOMEWORK) {
            throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE,
                    "Bài nộp không thuộc nội dung bài tập");
        }
        assertCanGrade(homework.getCourse(), principal);

        BigDecimal maxScore = resolveMaxScore(homework);
        BigDecimal score = request.getScore().setScale(2, RoundingMode.HALF_UP);
        if (maxScore != null && score.compareTo(maxScore) > 0) {
            throw new AppException(ErrorCode.HOMEWORK_GRADE_OUT_OF_RANGE,
                    "Điểm tối đa của bài tập là " + maxScore.stripTrailingZeros().toPlainString());
        }

        BigDecimal oldScore = submission.getScore();
        Boolean oldPassed = submission.getPassed();

        submission.setScore(score);
        submission.setFeedback(request.getFeedback());
        submission.setGradedDate(LocalDateTime.now());
        Boolean passed = resolvePassed(homework, score);
        submission.setPassed(passed);
        submission = submissionRepository.save(submission);

        // Lịch sử: chỉ ghi khi có thay đổi thực sự (điểm hoặc kết quả đạt).
        if (!sameScore(oldScore, score) || !samePassed(oldPassed, passed)) {
            User grader = userRepository.findById(principal.getUserId()).orElse(null);
            historyRepository.save(HomeworkGradeHistory.builder()
                    .submission(submission)
                    .gradedBy(grader)
                    .oldScore(oldScore)
                    .newScore(score)
                    .passed(passed)
                    .feedback(request.getFeedback())
                    .gradedAt(LocalDateTime.now())
                    .build());
        }

        log.info("Mentor {} chấm bài nộp {}: {} -> {} điểm, passed={}",
                principal.getUserId(), submissionId, oldScore, score, passed);

        return HomeworkSubmissionMapper.toResponse(submission, buildFileUrl(submission), null);
    }

    /**
     * Dựng URL có thời hạn cho file bài nộp (nếu có) — file là {@code authenticated} (private).
     */
    private String buildFileUrl(HomeworkSubmission submission) {
        if (submission == null
                || submission.getFilePublicId() == null
                || submission.getFilePublicId().isBlank()
                || !cloudinary.isConfigured()) {
            return null;
        }
        return cloudinary.buildAuthenticatedUrl(
                submission.getFilePublicId(),
                submission.getFileResourceType(),
                null);
    }

    /**
     * Lịch sử chấm của một bài nộp (mới nhất trước).
     */
    @Transactional(readOnly = true)
    public List<HomeworkGradeHistoryResponse> getHistory(UUID submissionId) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND));

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        assertCanGrade(submission.getHomeworkContent() != null
                ? submission.getHomeworkContent().getCourse() : null, principal);

        return historyRepository.findBySubmission_SubmissionIdOrderByGradedAtDesc(submissionId).stream()
                .map(h -> HomeworkGradeHistoryResponse.builder()
                        .historyId(h.getHistoryId())
                        .gradedByUserId(h.getGradedBy() != null ? h.getGradedBy().getUserId() : null)
                        .gradedByName(h.getGradedBy() != null ? h.getGradedBy().getFullName() : null)
                        .oldScore(h.getOldScore())
                        .newScore(h.getNewScore())
                        .passed(h.getPassed())
                        .feedback(h.getFeedback())
                        .gradedAt(h.getGradedAt())
                        .build())
                .toList();
    }

    /**
     * Admin chấm mọi bài; mentor chỉ chấm bài của khóa học mình phụ trách.
     */
    public void assertCanGrade(Course course, UserPrincipal principal) {
        if ("ADMIN".equalsIgnoreCase(principal.getRoleName())) {
            return;
        }
        boolean isCourseMentor = "MENTOR".equalsIgnoreCase(principal.getRoleName())
                && course != null
                && course.getMentor() != null
                && course.getMentor().getUserId().equals(principal.getUserId());
        if (!isCourseMentor) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }

    /**
     * Điểm tối đa của bài tập (đọc từ {@code content_data.maxScore}); null = không giới hạn.
     */
    public BigDecimal resolveMaxScore(CourseContent homework) {
        if (homework == null || homework.getContentData() == null) {
            return null;
        }
        BigDecimal value = HomeworkContentData.readNumber(homework.getContentData(), "maxScore");
        return value != null && value.signum() > 0 ? value : null;
    }

    /**
     * Tính đạt/không đạt.
     * <ul>
     *     <li>Có {@code maxScore} + {@code passingScore(%)} → đạt khi
     *         {@code score >= maxScore * passingScore / 100};</li>
     *     <li>Chỉ có {@code maxScore} → đạt khi {@code score >= 50%} (quy ước mặc định);</li>
     *     <li>Không có gì → luôn đạt (không có ngưỡng).</li>
     * </ul>
     */
    public Boolean resolvePassed(CourseContent homework, BigDecimal score) {
        BigDecimal maxScore = resolveMaxScore(homework);
        if (maxScore == null || score == null) {
            return Boolean.TRUE;
        }
        BigDecimal passingScore = readPassingScore(homework);
        BigDecimal threshold = passingScore != null
                ? maxScore.multiply(passingScore).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : maxScore.multiply(BigDecimal.valueOf(0.5)).setScale(2, RoundingMode.HALF_UP);
        return score.compareTo(threshold) >= 0;
    }

    /**
     * Tính kết quả đạt/không đạt theo TỈ LỆ phần trăm.
     * <p>
     * Dùng cho bài TRẮC NGHIỆM: điểm lượt thi theo thang của ĐỀ (VD 0–30),
     * còn {@code maxScore} của bài tập có thể là thang khác (VD 0–10) → phải
     * so sánh theo phần trăm, không so trực tiếp.
     *
     * @param score     điểm đạt được (thang của đề)
     * @param maxScore  điểm tối đa của lượt thi (thang của đề); null/0 = không xác định
     * @return true/false, hoặc null khi không xác định được tỉ lệ
     */
    public Boolean resolvePassedByRatio(CourseContent homework, BigDecimal score, BigDecimal maxScore) {
        if (score == null || maxScore == null || maxScore.signum() <= 0) {
            return null;
        }
        BigDecimal percentage = score.multiply(BigDecimal.valueOf(100))
                .divide(maxScore, 2, RoundingMode.HALF_UP);
        BigDecimal passingScore = readPassingScore(homework);
        BigDecimal threshold = passingScore != null ? passingScore : BigDecimal.valueOf(50);
        return percentage.compareTo(threshold) >= 0;
    }

    /** Điểm đạt (%) trong {@code content_data.passingScore}; null = dùng mặc định 50%. */
    private BigDecimal readPassingScore(CourseContent homework) {
        if (homework == null || homework.getContentData() == null) {
            return null;
        }
        BigDecimal value = HomeworkContentData.readNumber(homework.getContentData(), "passingScore");
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            return null;
        }
        return value;
    }

    private static boolean sameScore(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }

    private static boolean samePassed(Boolean a, Boolean b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
