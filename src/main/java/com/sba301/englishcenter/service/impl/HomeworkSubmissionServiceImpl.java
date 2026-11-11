package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.AttemptStatus;
import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.common.enums.NotificationType;
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
import com.sba301.englishcenter.service.HomeworkAiGradingService;
import com.sba301.englishcenter.service.HomeworkContentData;
import com.sba301.englishcenter.service.HomeworkGraderAssignmentService;
import com.sba301.englishcenter.service.HomeworkGradingService;
import com.sba301.englishcenter.service.HomeworkSubmissionMapper;
import com.sba301.englishcenter.service.HomeworkSubmissionService;
import com.sba301.englishcenter.service.MentorHomeworkService;
import com.sba301.englishcenter.service.NotificationService;
import com.sba301.englishcenter.util.JsonUtil;
import com.sba301.englishcenter.util.SessionVariableHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    /** Bài ghi âm / nộp file âm thanh (bài tập Speaking). */
    private static final String TYPE_AUDIO = "audio";
    /** Dung lượng tối đa của file PDF bài nộp. */
    private static final long MAX_PDF_BYTES = 20L * 1024 * 1024;
    /** Dung lượng tối đa của file âm thanh bài nộp (khớp giới hạn audio/video của Cloudinary). */
    private static final long MAX_AUDIO_BYTES = 100L * 1024 * 1024;
    /** Các đuôi file được coi là âm thanh → dùng giới hạn dung lượng lớn hơn. */
    private static final Set<String> AUDIO_FILE_EXTENSIONS =
            Set.of("mp3", "wav", "m4a", "aac", "ogg", "webm", "mp4", "mov");
    /** Định dạng nộp mặc định khi Admin không cấu hình (giữ hành vi cũ: chỉ PDF). */
    private static final String DEFAULT_ALLOWED_FILE_TYPE = "pdf";
    private static final java.time.ZoneId VN_ZONE = java.time.ZoneId.of("Asia/Ho_Chi_Minh");

    private final HomeworkSubmissionRepository submissionRepository;
    private final CourseContentRepository courseContentRepository;
    private final UserRepository userRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final SessionVariableHelper sessionVariableHelper;
    private final EnrollmentService enrollments;
    private final HomeworkGradingService gradingService;
    private final HomeworkGraderAssignmentService assignmentService;
    private final HomeworkAiGradingService aiGradingService;
    private final CloudinaryService cloudinary;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;
    // Bài tập riêng của mentor (V27, không thuộc khóa học): quyền làm bài + hạn nộp theo lộ trình
    private final MentorHomeworkService mentorHomeworkService;

    @Override
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

        assertCanAccess(homework, studentId);
        sessionVariableHelper.setCurrentUserId(studentId);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String submissionType = resolveSubmissionType(homework, request);
        assertSubmissionTypeMatches(homework, submissionType);
        assertCanAttempt(homework, studentId);

        // --- Ghi bài nộp (transaction riêng, commit trước khi gọi AI) ---
        HomeworkSubmission submission = new TransactionTemplate(transactionManager).execute(status -> {
            HomeworkSubmission s = newSubmission(homework, student, request, submissionType);

            switch (submissionType) {
                case TYPE_MULTIPLE_CHOICE -> applyMultipleChoice(s, request, homework);
                case TYPE_FILE -> applyFile(s, request);
                case TYPE_AUDIO -> applyAudio(s, request);
                default -> applyText(s, request);
            }

            // Gán người chấm: học viên đã mua gói mentor 1-1 → mentor đó chấm tay;
            // học viên chưa mua mentor → AI chấm tự động
            // (bài trắc nghiệm vốn đã tự chấm).
            assignmentService.assignGrader(s, studentId);

            return submissionRepository.save(s);
        });
        if (submission == null) {
            throw new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND);
        }

        log.info("Học viên {} nộp bài tập {} ({}) -> {}, chấm bởi: {}",
                studentId, homework.getContentId(), submissionType, submission.getSubmissionId(),
                submission.getGradedByType());

        // --- Học viên KHÔNG mua mentor → AI chấm tự động ngay ---
        // Gọi NGOÀI transaction vừa commit (AI chậm 10-60s, không giữ khoá dòng DB).
        // Nếu AI lỗi (chưa cấu hình API key / lỗi mạng) → bài vẫn nằm chờ, không chặn nộp.
        // Bài tập riêng của mentor (V27): luôn do mentor 1-1 chấm tay, không gọi AI
        if (!TYPE_MULTIPLE_CHOICE.equalsIgnoreCase(submissionType)
                && !mentorHomeworkService.isMentorHomework(homework)) {
            try {
                aiGradingService.gradeByAi(submission.getSubmissionId(), studentId);
            } catch (Exception ex) {
                log.warn("AI chấm bài nộp {} thất bại (bài vẫn nằm chờ chấm): {}",
                        submission.getSubmissionId(), ex.getMessage());
            }
        }

        notifyGraders(homework, student, submission);

        // Đọc lại để lấy điểm vừa được AI chấm (nếu có).
        HomeworkSubmission latest = submissionRepository.findById(submission.getSubmissionId())
                .orElse(submission);
        return toSubmissionResponse(latest, true);
    }

    /**
     * Báo cho người chấm biết có bài mới.
     * <p>
     * - Học viên ĐÃ mua gói mentor 1-1 → báo mentor đó (người được giao chấm).
     * - Học viên KHÔNG mua mentor → AI chấm tự động → không báo ai.
     * <p>
     * Bài trắc nghiệm được chấm tự động nên không cần báo.
     */
    private void notifyGraders(CourseContent homework, User student, HomeworkSubmission submission) {
        if (TYPE_MULTIPLE_CHOICE.equalsIgnoreCase(submission.getSubmissionType())) {
            return;
        }
        // Không có mentor nào được giao chấm → AI lo, không cần báo.
        UUID mentorId = submission.getAssignedGraderId();
        if (mentorId == null) {
            return;
        }
        String title = "Bài tập mới cần chấm";
        String message = student.getFullName() + " vừa nộp bài \""
                + (homework.getTitle() == null ? "Bài tập" : homework.getTitle())
                + "\". Vào mục Chấm bài tập để chấm.";
        notificationService.notify(mentorId, NotificationType.homework_deadline, title, message);
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
        applyUploadedFile(submission, request, resolveAllowedFileTypes(submission));
    }

    /**
     * Bài ghi âm: học viên upload file âm thanh (mp3/wav/m4a…).
     * <p>
     * Dùng chung cơ chế lưu file của {@link #applyFile} — chỉ khác ở tập định dạng
     * được phép (mặc định lấy theo cấu hình của bài tập, không cấu hình → audio).
     */
    private void applyAudio(HomeworkSubmission submission, CreateHomeworkSubmissionRequest request) {
        applyUploadedFile(submission, request, resolveAllowedFileTypes(submission));
    }

    /**
     * Lưu metadata file đã upload lên Cloudinary + validate định dạng.
     *
     * @param allowed các định dạng được phép (đuôi file, không dấu chấm)
     */
    private void applyUploadedFile(HomeworkSubmission submission,
                                  CreateHomeworkSubmissionRequest request,
                                  List<String> allowed) {
        String publicId = request.getFilePublicId();
        // Chỉ nhận file đã upload qua chữ ký BE cấp (không nhận URL tự do từ client).
        if (publicId == null || publicId.isBlank()) {
            throw new AppException(ErrorCode.HOMEWORK_FILE_REQUIRED);
        }
        String format = request.getFileFormat() == null ? "" : request.getFileFormat().trim().toLowerCase();
        String name = request.getFileOriginalName() == null ? "" : request.getFileOriginalName().trim().toLowerCase();
        // Định dạng được phép do Admin cấu hình trong content_data.allowedFileTypes.
        if (!matchesAllowedFormat(allowed, format, name, publicId)) {
            throw new AppException(ErrorCode.HOMEWORK_FILE_TYPE_NOT_ALLOWED,
                    "Bài nộp chỉ nhận file: " + String.join(", ", allowed).toUpperCase());
        }
        if (request.getFileBytes() != null && request.getFileBytes() > maxBytesFor(request)) {
            throw new AppException(ErrorCode.HOMEWORK_FILE_TOO_LARGE,
                    "Dung lượng tối đa cho loại file này là "
                            + (maxBytesFor(request) / (1024 * 1024)) + " MB");
        }
        submission.setFilePublicId(publicId);
        submission.setFileResourceType(request.getFileResourceType());
        submission.setFileFormat(request.getFileFormat());
        submission.setFileBytes(request.getFileBytes());
        submission.setFileOriginalName(request.getFileOriginalName());
    }

    /**
     * Định dạng file được phép nộp cho bài tập này.
     *
     * <p>Đọc từ {@code content_data.allowedFileTypes} do Admin cấu hình.
     * Thiếu → mặc định chỉ nhận PDF (hành vi cũ).
     */
    private List<String> resolveAllowedFileTypes(HomeworkSubmission submission) {
        CourseContent homework = submission.getHomeworkContent();
        List<String> configured = homework == null
                ? null
                : HomeworkContentData.readAllowedFileTypes(homework.getContentData());
        if (configured != null && !configured.isEmpty()) {
            return configured;
        }
        // Bài ghi âm (Speaking) → mặc định các định dạng audio.
        String type = homework == null
                ? null
                : HomeworkContentData.readText(homework.getContentData(), "submissionType");
        if (type != null && "audio".equalsIgnoreCase(type.trim())) {
            return List.of("mp3", "wav", "m4a", "aac", "ogg", "webm");
        }
        return List.of(DEFAULT_ALLOWED_FILE_TYPE);
    }

    /**
     * Giới hạn dung lượng cho file bài nộp.
     *
     * <p>File tài liệu (pdf/docx…) → {@link #MAX_PDF_BYTES}.
     * File âm thanh (mp3/wav/m4a…) → dung lượng lớn hơn, khớp giới hạn
     * {@code audio/video} của {@code UploadService} (100 MB).
     */
    private long maxBytesFor(CreateHomeworkSubmissionRequest request) {
        String format = request.getFileFormat() == null ? "" : request.getFileFormat().trim().toLowerCase();
        return AUDIO_FILE_EXTENSIONS.contains(format) ? MAX_AUDIO_BYTES : MAX_PDF_BYTES;
    }

    /**
     * File có khớp ít nhất một định dạng được phép không.
     *
     * <p>Kiểm tra theo thứ tự: {@code fileFormat} → đuôi của tên file → đuôi của public_id.
     */
    private boolean matchesAllowedFormat(List<String> allowed, String format, String name, String publicId) {
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        if (!format.isBlank() && allowed.contains(format)) {
            return true;
        }
        if (name.contains(".")) {
            String ext = name.substring(name.lastIndexOf('.') + 1);
            if (allowed.contains(ext)) {
                return true;
            }
        }
        String pid = publicId.toLowerCase();
        if (pid.contains(".")) {
            String ext = pid.substring(pid.lastIndexOf('.') + 1);
            if (allowed.contains(ext)) {
                return true;
            }
        }
        return false;
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
        if (!TYPE_TEXT.equals(type) && !TYPE_FILE.equals(type)
                && !TYPE_MULTIPLE_CHOICE.equals(type) && !TYPE_AUDIO.equals(type)) {
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

    @Override
    public void assertCanAttempt(CourseContent homework, UUID studentId) {
        assertDueDateNotPassed(homework, studentId);
        assertAttemptsNotExceeded(homework, studentId);
    }

    /**
     * Quyền làm bài: bài của khóa học → phải ghi danh khóa học <b>AND</b> đã mua
     * gói kèm 1-1 (bài tập khóa học cần mentor 1-1 chấm tay);
     * bài riêng của mentor (V27, course = null) → phải ghi danh lộ trình chứa bài.
     */
    private void assertCanAccess(CourseContent homework, UUID studentId) {
        if (homework.getCourse() == null) {
            mentorHomeworkService.assertStudentCanAccess(studentId, homework);
            return;
        }
        if (!enrollments.isEnrolled(studentId, homework.getCourse().getCourseId())) {
            throw new AppException(ErrorCode.ENROLLMENT_REQUIRED);
        }
        // Bài tập thuộc KHÓA HỌC chỉ mở khi học viên đã mua gói kèm 1-1 →
        // đây chính là điều kiện MỞ TAB "Bài tập" ở màn khóa học. Chưa mua → chặn
        // luôn (kể cả khi gọi API trực tiếp), tránh lách qua client.
        if (!assignmentService.hasMentor(studentId)) {
            throw new AppException(ErrorCode.HOMEWORK_MENTOR_PLAN_REQUIRED);
        }
    }

    /** Hạn nộp đã qua → không cho nộp. */
    private void assertDueDateNotPassed(CourseContent homework, UUID studentId) {
        // Bài riêng của mentor (V27): hạn = ngày ghi danh lộ trình + dueDays (giờ Việt Nam)
        if (mentorHomeworkService.isMentorHomework(homework)) {
            LocalDate due = mentorHomeworkService.resolveDueDate(homework, studentId);
            if (due != null && LocalDate.now(VN_ZONE).isAfter(due)) {
                throw new AppException(ErrorCode.HOMEWORK_DUE_DATE_PASSED,
                        "Bài tập đã hết hạn nộp (" + due + ")");
            }
            return;
        }
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
                    submission.getFileFormat(),
                    null);
        }
        return HomeworkSubmissionMapper.toResponse(submission, fileUrl, null);
    }
}
