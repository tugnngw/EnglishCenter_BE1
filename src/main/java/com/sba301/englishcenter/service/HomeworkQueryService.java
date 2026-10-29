package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.common.enums.ContentType;
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

    private final HomeworkSubmissionRepository submissionRepository;
    private final CourseContentRepository courseContentRepository;
    private final HomeworkGradeHistoryRepository historyRepository;
    private final CloudinaryService cloudinary;

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
     * Hàng đợi chấm của mentor: các bài nộp CHƯA chấm trong khóa học mình dạy.
     * <p>
     * Admin thấy tất cả các bài nộp (kể cả đã chấm — để tra cứu).
     */
    @Transactional(readOnly = true)
    public PageResponse<HomeworkSubmissionResponse> getMentorQueue(Pageable pageable, boolean pendingOnly) {
        UserPrincipal principal = SecurityUtils.getCurrentUserPrincipal()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        Page<HomeworkSubmission> page;
        if ("ADMIN".equalsIgnoreCase(principal.getRoleName())) {
            page = pendingOnly
                    ? submissionRepository.findPendingGrading(pageable)
                    : submissionRepository.findAll(pageable);
        } else {
            UUID mentorId = principal.getUserId();
            page = pendingOnly
                    ? submissionRepository.findPendingGradingForMentor(mentorId, pageable)
                    : submissionRepository.findAllForMentor(mentorId, pageable);
        }

        return PageResponse.<HomeworkSubmissionResponse>builder()
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalPages(page.getTotalPages())
                .totalElements(page.getTotalElements())
                .data(page.getContent().stream()
                        .map(s -> HomeworkSubmissionMapper.toResponse(s, buildFileUrl(s), null))
                        .toList())
                .build();
    }

    /**
     * Chi tiết bài nộp cho mentor (kèm lịch sử chấm).
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
        Course course = homework.getCourse();
        boolean isCourseMentor = "MENTOR".equalsIgnoreCase(principal.getRoleName())
                && course != null
                && course.getMentor() != null
                && course.getMentor().getUserId().equals(principal.getUserId());
        if (!"ADMIN".equalsIgnoreCase(principal.getRoleName()) && !isCourseMentor) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<com.sba301.englishcenter.dto.response.HomeworkGradeHistoryResponse> history =
                historyRepository.findBySubmission_SubmissionIdOrderByGradedAtDesc(submissionId).stream()
                        .map(HomeworkSubmissionMapper::toHistoryResponse)
                        .toList();
        return HomeworkSubmissionMapper.toResponse(submission, buildFileUrl(submission), history);
    }
}
