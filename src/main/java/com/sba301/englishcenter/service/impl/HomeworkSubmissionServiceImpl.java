package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.dto.request.CreateHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.request.GradeHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.ExamAttempt;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.ExamAttemptRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.CloudinaryService;
import com.sba301.englishcenter.service.EnrollmentService;
import com.sba301.englishcenter.service.HomeworkContentData;
import com.sba301.englishcenter.service.HomeworkGradingService;
import com.sba301.englishcenter.service.HomeworkSubmissionMapper;
import com.sba301.englishcenter.service.HomeworkSubmissionService;
import com.sba301.englishcenter.util.JsonUtil;
import com.sba301.englishcenter.util.SessionVariableHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Luồng nộp bài tập của học viên.
 * <p>
 * Hỗ trợ 3 hình thức nộp (theo {@code content_data.submissionType}):
 * <ul>
 *     <li>{@code text} — học viên gõ trả lời vào {@code answer_content};</li>
 *     <li>{@code file} — học viên upload lên Cloudinary, BE lưu public_id + metadata;</li>
 *     <li>{@code multiple_choice} — học viên làm bài trong phòng thi thật
 *         ({@code exam_attempts}), BE chấm tự động và tính {@code passed} lúc nộp.</li>
 * </ul>
 * Tương thích ngược: client cũ chỉ gửi {@code answerContent} / {@code fileUrl}
 * vẫn chạy y như trước (thiếu {@code submissionType} → mặc định {@code text}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkSubmissionServiceImpl implements HomeworkSubmissionService {

    private static final String TYPE_TEXT = "text";
    private static final String TYPE_FILE = "file";
    private static final String TYPE_MULTIPLE_CHOICE = "multiple_choice";

    private final HomeworkSubmissionRepository submissionRepository;
    private final CourseContentRepository courseContentRepository;
    private final UserRepository userRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final SessionVariableHelper sessionVariableHelper;
    private final EnrollmentService enrollments;
    private final HomeworkGradingService gradingService;
    private final CloudinaryService cloudinary;

    @Override
    @Transactional
    public HomeworkSubmissionResponse submitHomework(CreateHomeworkSubmissionRequest request) {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        CourseContent homework = courseContentRepository.findById(request.getHomeworkContentId())
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));

        // Chỉ chấp nhận nội dung có content_type = homework
        if (homework.getContentType() != ContentType.HOMEWORK) {
            throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE,
                    "Nội dung trừ tới không phải bài tập (homework)");
        }

        if (!enrollments.isEnrolled(studentId, homework.getCourse().getCourseId())) {
            throw new AppException(ErrorCode.ENROLLMENT_REQUIRED);
        }
        sessionVariableHelper.setCurrentUserId(studentId);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String submissionType = resolveSubmissionType(homework, request);
        assertSubmissionTypeMatches(homework, submissionType);
        assertDueDateNotPassed(homework);
        assertAttemptsNotExceeded(homework, studentId);

        HomeworkSubmission submission = newSubmission(homework, student, request, submissionType);

        switch (submissionType) {
            case TYPE_MULTIPLE_CHOICE -> applyMultipleChoice(submission, request, homework);
            case TYPE_FILE -> applyFile(submission, request);
            default -> applyText(submission, request);
        }

        submission = submissionRepository.save(submission);
        log.info("Học viên {} nộp bài tập {} ({}) -> {}",
                studentId, homework.getContentId(), submissionType, submission.getSubmissionId());

        return toSubmissionResponse(submission, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeworkSubmissionResponse> getMySubmissions() {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        return submissionRepository.findByStudent_UserIdOrderBySubmittedDateDesc(studentId).stream()
                .map(s -> toSubmissionResponse(s, true))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HomeworkSubmissionResponse getSubmissionById(UUID submissionId) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND));

        // Chỉ chính chủ, admin, hoặc mentor phụ trách khóa học được xem chi tiết.
        // (Trước V8 endpoint này không kiểm tra gì; giữ nguyên khả năng truy cập
        //  của admin/mentor để không đổi hành vi, đồng thời chặn học viên xem
        //  bài nộp của bạn khác.)
        UUID currentUserId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        boolean isOwner = submission.getStudent() != null
                && currentUserId.equals(submission.getStudent().getUserId());
        if (!isOwner && !canViewAsMentor(submission, currentUserId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return toSubmissionResponse(submission, true);
    }

    /** Mentor phụ trách khóa học chứa bài tập, hoặc admin, được xem bài nộp. */
    private boolean canViewAsMentor(HomeworkSubmission submission, UUID currentUserId) {
        if (isAdmin()) {
            return true;
        }
        CourseContent homework = submission.getHomeworkContent();
        if (homework == null || homework.getCourse() == null) {
            return false;
        }
        var mentor = homework.getCourse().getMentor();
        return mentor != null && currentUserId.equals(mentor.getUserId());
    }

    @Override
    @Transactional
    public HomeworkSubmissionResponse gradeSubmission(UUID submissionId,
                                                       GradeHomeworkSubmissionRequest request) {
        UUID graderId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        sessionVariableHelper.setCurrentUserId(graderId);

        // Chuyển toàn bộ nghiệp vụ chấm điểm sang HomeworkGradingService
        // (tính passed + ghi lịch sử + phân quyền mentor).
        return gradingService.grade(submissionId, request);
    }

    /* =====================================================
     * Áp dụng theo hình thức nộp
     * ===================================================== */

    private void applyText(HomeworkSubmission submission, CreateHomeworkSubmissionRequest request) {
        String answer = request.getAnswerContent();
        if (answer == null || answer.isBlank()) {
            throw new AppException(ErrorCode.HOMEWORK_TEXT_REQUIRED);
        }
        submission.setAnswerContent(answer);
    }

    private void applyFile(HomeworkSubmission submission, CreateHomeworkSubmissionRequest request) {
        String publicId = request.getFilePublicId();
        if (publicId == null || publicId.isBlank()) {
            // Tương thích ngược: client cũ gửi URL trực tiếp.
            if (request.getFileUrl() == null || request.getFileUrl().isBlank()) {
                throw new AppException(ErrorCode.HOMEWORK_FILE_REQUIRED);
            }
            submission.setFileUrl(request.getFileUrl());
            return;
        }
        submission.setFilePublicId(publicId);
        submission.setFileResourceType(request.getFileResourceType());
        submission.setFileFormat(request.getFileFormat());
        submission.setFileBytes(request.getFileBytes());
        submission.setFileOriginalName(request.getFileOriginalName());
    }

    /**
     * Bài trắc nghiệm: lấy điểm từ lượt thi thật ({@code exam_attempts}).
     * <p>
     * Học viên làm bài trong phòng thi hiện có; lúc nộp bài tập, BE đọc điểm
     * của lượt thi đó, lưu vào bài nộp và tính {@code passed} ngay.
     */
    private void applyMultipleChoice(HomeworkSubmission submission,
                                    CreateHomeworkSubmissionRequest request,
                                    CourseContent homework) {
        UUID attemptId = request.getExamAttemptId();
        if (attemptId == null) {
            throw new AppException(ErrorCode.HOMEWORK_ANSWER_REQUIRED);
        }

        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));

        // Lượt thi phải thuộc đúng học viên đang nộp bài.
        if (attempt.getStudent() == null || !attempt.getStudent().getUserId()
                .equals(submission.getStudent().getUserId())) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_FOUND);
        }
        // Lượt thi phải đúng đề mà bài tập trỏ tới (nếu bài tập có liên kết đề).
        if (homework.getExam() != null && attempt.getExam() != null
                && !homework.getExam().getExamId().equals(attempt.getExam().getExamId())) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_FOUND);
        }
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_SUBMITTED);
        }

        BigDecimal score = attempt.getScore() != null ? attempt.getScore() : BigDecimal.ZERO;
        BigDecimal maxScore = attempt.getMaxScore() != null ? attempt.getMaxScore() : BigDecimal.ZERO;

        submission.setExamAttempt(attempt);
        submission.setScore(score.setScale(2, RoundingMode.HALF_UP));
        submission.setGradedDate(LocalDateTime.now());

        // Điểm lượt thi theo thang của ĐỀ (VD 0–30) — khác thang `maxScore` của bài tập (VD 0–10)
        // → tính đạt/không đạt theo TỈ LỆ phần trăm.
        Boolean passed = gradingService.resolvePassedByRatio(homework, score, maxScore);
        submission.setPassed(passed != null ? passed : gradingService.resolvePassed(homework, score));

        // Ghi luôn tổng điểm để hiển thị "x / y" cho học viên.
        submission.setAnswerContent("score=" + score.stripTrailingZeros().toPlainString()
                + ";max=" + maxScore.stripTrailingZeros().toPlainString());
    }

    /* =====================================================
     * Validate
     * ===================================================== */

    /**
     * Hình thức nộp: ưu tiên giá trị client gửi, fallback đọc từ
     * {@code content_data.submissionType} (mặc định {@code text} cho dữ liệu cũ).
     */
    private String resolveSubmissionType(CourseContent homework, CreateHomeworkSubmissionRequest request) {
        String raw = request.getSubmissionType();
        if (raw == null || raw.isBlank()) {
            raw = HomeworkContentData.readText(homework.getContentData(), "submissionType");
        }
        if (raw == null || raw.isBlank()) {
            return TYPE_TEXT;
        }
        String type = raw.trim().toLowerCase();
        if (!TYPE_TEXT.equals(type) && !TYPE_FILE.equals(type) && !TYPE_MULTIPLE_CHOICE.equals(type)) {
            throw new AppException(ErrorCode.HOMEWORK_SUBMISSION_TYPE_MISMATCH,
                    "Hình thức nộp không hợp lệ: " + raw);
        }
        return type;
    }

    /**
     * Hình thức nộp của bài tập phải khớp với loại bài admin đã cấu hình.
     * <p>
     * Chỉ kiểm tra khi bài tập có cấu hình rõ ràng (dữ liệu cũ thiếu
     * {@code submissionType} → không chặn, giữ hành vi cũ).
     */
    private void assertSubmissionTypeMatches(CourseContent homework, String submissionType) {
        String configured = HomeworkContentData.readText(homework.getContentData(), "submissionType");
        if (configured == null || configured.isBlank()) {
            return;
        }
        if (!configured.trim().equalsIgnoreCase(submissionType)) {
            throw new AppException(ErrorCode.HOMEWORK_SUBMISSION_TYPE_MISMATCH,
                    "Bài tập này yêu cầu hình thức nộp: " + configured);
        }
    }

    /** Hạn nộp đã qua → không cho nộp. */
    private void assertDueDateNotPassed(CourseContent homework) {
        String raw = HomeworkContentData.readText(homework.getContentData(), "dueDate");
        if (raw == null || raw.isBlank()) {
            return;
        }
        LocalDate dueDate;
        try {
            dueDate = LocalDate.parse(raw.trim());
        } catch (Exception ex) {
            log.warn("Bài tập {} có dueDate không đúng định dạng: {}", homework.getContentId(), raw);
            return;
        }
        if (LocalDate.now().isAfter(dueDate)) {
            throw new AppException(ErrorCode.HOMEWORK_DUE_DATE_PASSED,
                    "Bài tập đã hết hạn nộp (" + dueDate + ")");
        }
    }

    /** Vượt số lần làm tối đa → không cho nộp. */
    private void assertAttemptsNotExceeded(CourseContent homework, UUID studentId) {
        Integer maxAttempts = readMaxAttempts(homework);
        if (maxAttempts == null || maxAttempts <= 0) {
            return;
        }
        long used = submissionRepository.countByHomeworkContent_ContentIdAndStudent_UserId(
                homework.getContentId(), studentId);
        if (used >= maxAttempts) {
            throw new AppException(ErrorCode.HOMEWORK_ATTEMPT_LIMIT_REACHED,
                    "Bài tập này tối đa " + maxAttempts + " lần nộp");
        }
    }

    private Integer readMaxAttempts(CourseContent homework) {
        BigDecimal value = HomeworkContentData.readNumber(homework.getContentData(), "maxAttempts");
        if (value == null) {
            return null;
        }
        int attempts = value.intValue();
        return attempts > 0 ? attempts : null;
    }

    private HomeworkSubmission newSubmission(CourseContent homework, User student,
                                             CreateHomeworkSubmissionRequest request,
                                             String submissionType) {
        return HomeworkSubmission.builder()
                .homeworkContent(homework)
                .student(student)
                .submissionType(submissionType)
                .answerContent(request.getAnswerContent())
                .fileUrl(request.getFileUrl())
                .submittedDate(LocalDateTime.now())
                .build();
    }

    private boolean isAdmin() {
        return SecurityUtils.getCurrentUserPrincipal()
                .map(p -> "ADMIN".equalsIgnoreCase(p.getRoleName()))
                .orElse(false);
    }

    /**
     * Map sang response. File bài nộp dùng URL CÓ THỜI HẠN — chỉ dựng khi
     * người xem có quyền (chính chủ hoặc mentor/admin chấm bài đó).
     */
    private HomeworkSubmissionResponse toSubmissionResponse(HomeworkSubmission submission, boolean includeFileUrl) {
        String fileUrl = null;
        if (includeFileUrl && submission.getFilePublicId() != null
                && !submission.getFilePublicId().isBlank()
                && cloudinary.isConfigured()) {
            fileUrl = cloudinary.buildAuthenticatedUrl(
                    submission.getFilePublicId(),
                    submission.getFileResourceType(),
                    null);
        }
        return HomeworkSubmissionMapper.toResponse(submission, fileUrl, null);
    }
}
