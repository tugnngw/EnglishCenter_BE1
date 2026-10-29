package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.dto.request.AnswerItemRequest;
import com.sba301.englishcenter.dto.request.SubmitAttemptRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.Exam;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Luồng làm bài TRẮC NGHIỆM cho một bài tập (Dev 2.5).
 * <p>
 * Bài tập trắc nghiệm dùng chung máy chấm {@link ExamGrader} của module Exam,
 * nhưng có LUỒNG RIÊNG:
 * <ol>
 *     <li>{@link #startAttempt} — mở lượt thi thật cho đề mà bài tập trỏ tới;</li>
 *     <li>{@link #submitAttempt} — nộp lượt thi (chấm tự động) rồi TỰ TẠO bản ghi
 *         {@code homework_submissions} gắn với lượt thi đó.</li>
 * </ol>
 * <p>
 * Nhờ vậy học viên làm bài ngay trong bài tập (không phải rời sang phòng thi của
 * module Exam) và bài nộp luôn gắn đúng lượt thi đã nộp.
 * <p>
 * <b>Không sửa code của module Exam</b> — service này chỉ GỌI vào
 * {@link ExamAttemptService} (interface công khai của team).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkExamService {

    /** Loại hình thức nộp của bài trắc nghiệm. */
    private static final String TYPE_MULTIPLE_CHOICE = "multiple_choice";

    private final CourseContentRepository courseContentRepository;
    private final HomeworkSubmissionRepository submissionRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final UserRepository userRepository;
    private final ExamAttemptService examAttemptService;
    private final HomeworkSubmissionService homeworkSubmissionService;
    private final HomeworkGradingService gradingService;

    /**
     * Mở lượt thi cho bài tập trắc nghiệm.
     * <p>
     * Nếu bài tập đã có lượt thi đang mở → trả lại lượt đó (không tạo trùng).
     */
    @Transactional
    public AttemptDetailResponse startAttempt(UUID homeworkContentId) {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        CourseContent homework = requireMultipleChoiceHomework(homeworkContentId);
        Exam exam = requireLinkedExam(homework);

        // `ExamAttemptService` tự kiểm tra quyền làm bài (enrollment / trả phí / trạng thái đề)
        // và tự trả lại lượt thi đang mở nếu có.
        AttemptDetailResponse attempt = examAttemptService.startAttempt(exam.getExamId());

        log.info("Học viên {} mở lượt thi cho bài tập {} -> attempt {}",
                studentId, homeworkContentId, attempt.getAttemptId());
        return attempt;
    }

    /**
     * Tải lượt thi (câu hỏi + đáp án đã lưu) để học viên tiếp tục làm bài.
     */
    @Transactional(readOnly = true)
    public AttemptDetailResponse getAttempt(UUID attemptId) {
        return examAttemptService.getAttempt(attemptId);
    }

    /**
     * Nộp bài trắc nghiệm: chấm tự động rồi tạo bài nộp.
     * <p>
     * Bài nộp được tạo bởi chính service này nên điểm và {@code passed} luôn
     * khớp với lượt thi vừa nộp.
     *
     * @return kết quả lượt thi kèm bài nộp đã tạo
     */
    @Transactional
    public AttemptResultResponse submitAttempt(UUID attemptId, List<AnswerItemRequest> answers) {
        SubmitAttemptRequest request = SubmitAttemptRequest.builder()
                .answers(answers)
                .build();
        AttemptResultResponse result = examAttemptService.submitAttempt(attemptId, request);
        createSubmissionFromAttempt(attemptId);
        return result;
    }

    /**
     * Tạo bài nộp từ một lượt thi đã nộp.
     * <p>
     * Idempotent: nếu lượt thi này đã có bài nộp → trả về bài nộp cũ, tránh
     * tạo trùng khi FE gọi lại.
     */
    @Transactional
    public HomeworkSubmissionResponse createSubmissionFromAttempt(UUID attemptId) {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));

        CourseContent homework = findHomeworkOf(attempt);
        if (homework == null) {
            throw new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND,
                    "Không tìm thấy bài tập ứng với lượt thi này");
        }

        // Đã nộp bài bài tập cho lượt thi này rồi → trả về bản ghi cũ.
        HomeworkSubmission existing = submissionRepository
                .findByHomeworkContent_ContentIdAndStudent_UserId(
                        homework.getContentId(), studentId)
                .orElse(null);
        if (existing != null && attempt.getAttemptId().equals(
                existing.getExamAttempt() != null ? existing.getExamAttempt().getAttemptId() : null)) {
            return homeworkSubmissionService.getSubmissionById(existing.getSubmissionId());
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        BigDecimal score = attempt.getScore() != null ? attempt.getScore() : BigDecimal.ZERO;
        BigDecimal maxScore = attempt.getMaxScore() != null ? attempt.getMaxScore() : BigDecimal.ZERO;

        HomeworkSubmission submission = HomeworkSubmission.builder()
                .homeworkContent(homework)
                .student(student)
                .submissionType(TYPE_MULTIPLE_CHOICE)
                .examAttempt(attempt)
                .score(score)
                .gradedDate(LocalDateTime.now())
                .submittedDate(LocalDateTime.now())
                .build();

        // Điểm lượt thi theo thang của ĐỀ → tính đạt/không đạt theo TỈ LỆ phần trăm.
        Boolean passed = gradingService.resolvePassedByRatio(homework, score, maxScore);
        submission.setPassed(passed != null ? passed : gradingService.resolvePassed(homework, score));

        // Ghi luôn tổng điểm để hiển thị "x / y" cho học viên.
        submission.setAnswerContent("score=" + score.stripTrailingZeros().toPlainString()
                + ";max=" + maxScore.stripTrailingZeros().toPlainString());

        HomeworkSubmission saved = submissionRepository.save(submission);
        log.info("Đã tạo bài nộp trắc nghiệm {} cho học viên {} (attempt {})",
                saved.getSubmissionId(), studentId, attemptId);

        return homeworkSubmissionService.getSubmissionById(saved.getSubmissionId());
    }

    /* =====================================================
     * Helpers
     * ===================================================== */

    private CourseContent requireMultipleChoiceHomework(UUID homeworkContentId) {
        CourseContent homework = courseContentRepository.findById(homeworkContentId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_CONTENT_NOT_FOUND));
        if (homework.getContentType() != ContentType.HOMEWORK) {
            throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE,
                    "Nội dung này không phải bài tập (homework)");
        }
        String type = HomeworkContentData.readText(homework.getContentData(), "submissionType");
        if (type != null && !type.isBlank()
                && !TYPE_MULTIPLE_CHOICE.equalsIgnoreCase(type.trim())) {
            throw new AppException(ErrorCode.HOMEWORK_SUBMISSION_TYPE_MISMATCH,
                    "Bài tập này không phải dạng trắc nghiệm");
        }
        return homework;
    }

    private Exam requireLinkedExam(CourseContent homework) {
        Exam exam = homework.getExam();
        if (exam == null) {
            throw new AppException(ErrorCode.COURSE_CONTENT_EXAM_REQUIRED,
                    "Bài tập trắc nghiệm chưa được liên kết đề thi");
        }
        return exam;
    }

    /** Tìm bài tập mà lượt thi này thuộc về (qua đề liên kết). */
    private CourseContent findHomeworkOf(ExamAttempt attempt) {
        Exam exam = attempt.getExam();
        if (exam == null) {
            return null;
        }
        List<CourseContent> candidates = courseContentRepository
                .findByContentTypeAndExam_ExamId(ContentType.HOMEWORK, exam.getExamId());
        return candidates.isEmpty() ? null : candidates.get(0);
    }
}
