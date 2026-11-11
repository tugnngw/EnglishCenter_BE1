package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.common.enums.MentoringPlanStatus;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.entity.Course;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.HomeworkGradeHistoryRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Truy vấn bài nộp bài tập.
 * <p>
 * <ul>
 *     <li>Học viên: xem bài nộp CỦA MÌNH, và xem trạng thái các bài tập trong khóa đã đăng ký;</li>
 *     <li>Mentor: xem bài nộp thuộc khóa học MÌNH DẠY (admin thấy tất cả).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class HomeworkQueryService {

    /**
     * Trạng thái gói kèm 1-1 ĐÃ THANH TOÁN → mentor đó được phép chấm bài tập
     * của học viên (dùng cho khóa học KHÔNG gán mentor).
     * <p>
     * CHỈ gói đã trả tiền ({@code active} / {@code completed}). Gói {@code pending_payment}
     * (đã bấm thanh toán, SePay chưa xác nhận) không được tính ở đây để tránh
     * mentor chưa thu tiền nhận bài của học viên đã trả tiền cho mentor khác.
     */
    private static final List<MentoringPlanStatus> MENTOR_PLAN_STATUSES =
            List.of(MentoringPlanStatus.active, MentoringPlanStatus.completed);

    private final HomeworkSubmissionRepository submissionRepository;
    private final CourseContentRepository courseContentRepository;
    private final HomeworkGradeHistoryRepository historyRepository;
    private final CloudinaryService cloudinary;
    private final HomeworkGradingService gradingService;

    /**
     * Dựng URL có thời hạn cho file bài nộp (nếu có).
     * <p>
     * File bài nộp là {@code authenticated} (private) → URL chỉ dùng được trong
     * thời gian ngắn, BE sinh mới mỗi lần gọi API.
     *
     * @return URL xem file, hoặc null nếu bài nộp không có file / Cloudinary chưa cấu hình
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
                submission.getFileFormat(),
                null);
    }

    /**
     * Bài nộp của học viên đang đăng nhập (mới nhất trước).
     */
    @Transactional(readOnly = true)
    public List<HomeworkSubmissionResponse> getMySubmissions() {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return submissionRepository.findByStudent_UserIdOrderBySubmittedDateDesc(studentId).stream()
                .map(s -> HomeworkSubmissionMapper.toResponse(s, buildFileUrl(s), null))
                .toList();
    }

    /**
     * Bài nộp CỦA học viên đang đăng nhập cho một bài tập cụ thể.
     */
    @Transactional(readOnly = true)
    public List<HomeworkSubmissionResponse> getMySubmissionsForHomework(UUID homeworkContentId) {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        return submissionRepository
                .findByHomeworkContent_ContentIdAndStudent_UserIdOrderBySubmittedDateDesc(
                        homeworkContentId, studentId)
                .stream()
                .map(s -> HomeworkSubmissionMapper.toResponse(s, buildFileUrl(s), null))
                .toList();
    }

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
    @Transactional(readOnly = true)
    public PageResponse<HomeworkSubmissionResponse> getMentorQueue(Pageable pageable, boolean pendingOnly, String source) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        // Chỉ MENTOR vào được màn hình chấm bài (admin chỉ tạo nội dung).
        if (!"MENTOR".equalsIgnoreCase(principal.getRoleName())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        UUID mentorId = principal.getUserId();
        String normalizedSource = normalizeSource(source);
        Page<HomeworkSubmission> page = pendingOnly
                ? submissionRepository.findPendingGradingForMentorWithStudents(
                        mentorId, MENTOR_PLAN_STATUSES, normalizedSource, pageable)
                : submissionRepository.findAllForMentorWithStudents(
                        mentorId, MENTOR_PLAN_STATUSES, normalizedSource, pageable);

        return PageResponse.<HomeworkSubmissionResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream()
                        .filter(s -> !isAutoGraded(s))
                        .map(s -> HomeworkSubmissionMapper.toResponse(s, buildFileUrl(s), null))
                        .toList())
                .build();
    }

    /**
     * Chuẩn hoá tham số {@code source}: chỉ nhận {@code all} / {@code course} / {@code mentor}.
     * Giá trị lạ → {@code all} (không lọc) để không mất bài nào khỏi hàng đợi.
     */
    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "all";
        }
        String value = source.trim().toLowerCase();
        return switch (value) {
            case "course", "mentor", "all" -> value;
            default -> "all";
        };
    }

    /**
     * Bài này do hệ thống tự chấm (Nhóm A — trắc nghiệm) → mentor KHÔNG chấm tay.
     * <p>
     * Bài trắc nghiệm: hệ thống tự chấm, mentor chỉ xem bảng thống kê.
     * Bài đã AI chấm xong: cũng không vào hàng đợi chấm tay.
     */
    private boolean isAutoGraded(HomeworkSubmission submission) {
        if (submission == null) {
            return false;
        }
        if ("multiple_choice".equalsIgnoreCase(submission.getSubmissionType())) {
            return true;
        }
        return submission.getGradedDate() != null
                && "ai".equalsIgnoreCase(submission.getGradedByType());
    }

    /**
     * Chi tiết bài nộp cho mentor (kèm lịch sử chấm + rubric chấm điểm).
     */
    @Transactional(readOnly = true)
    public HomeworkSubmissionResponse getForGrading(UUID submissionId) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND));

        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        CourseContent homework = submission.getHomeworkContent();
        if (homework == null || homework.getContentType() != ContentType.HOMEWORK) {
            throw new AppException(ErrorCode.COURSE_CONTENT_INVALID_TYPE,
                    "Bài nộp không thuộc nội dung bài tập");
        }
        // Chỉ MENTOR được chấm: mentor của khóa học, hoặc mentor dạy kèm 1-1 học viên.
        gradingService.assertCanGrade(submission, principal);

        List<com.sba301.englishcenter.dto.response.HomeworkGradeHistoryResponse> history =
                historyRepository.findBySubmission_SubmissionIdOrderByGradedAtDesc(submissionId).stream()
                        .map(HomeworkSubmissionMapper::toHistoryResponse)
                        .toList();
        HomeworkSubmissionResponse response =
                HomeworkSubmissionMapper.toResponse(submission, buildFileUrl(submission), history);

        // Bổ sung thông tin cho giao diện chấm song song: rubric, thang điểm.
        response.setRubric(HomeworkContentData.readRubric(homework.getContentData()));
        response.setMaxScore(gradingService.resolveMaxScore(homework));
        response.setPassingScore(HomeworkContentData.readNumber(homework.getContentData(), "passingScore"));
        response.setAutoGraded(isAutoGraded(submission));
        return response;
    }
}
