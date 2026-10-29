package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.HomeworkGradeHistoryResponse;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.entity.HomeworkGradeHistory;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ánh xạ {@link HomeworkSubmission} → {@link HomeworkSubmissionResponse}.
 * <p>
 * Tách riêng để 2 service (nộp bài của học viên và chấm điểm của mentor) dùng
 * chung một quy tắc map, tránh lệch hành vi.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HomeworkSubmissionMapper {

    /**
     * @param submission bài nộp
     * @param fileUrl    URL xem file ĐÃ CÓ THỜI HẠN (null = chưa dựng / không có quyền xem)
     * @param history    lịch sử chấm (chỉ mentor/admin thấy)
     */
    public static HomeworkSubmissionResponse toResponse(HomeworkSubmission submission,
                                                         String fileUrl,
                                                         List<HomeworkGradeHistoryResponse> history) {
        if (submission == null) {
            return null;
        }
        UserRef student = submission.getStudent() == null
                ? null
                : new UserRef(submission.getStudent().getUserId(),
                        submission.getStudent().getFullName(),
                        submission.getStudent().getEmail());

        boolean graded = submission.getGradedDate() != null;

        return HomeworkSubmissionResponse.builder()
                .submissionId(submission.getSubmissionId())
                .homeworkContentId(submission.getHomeworkContent() != null
                        ? submission.getHomeworkContent().getContentId() : null)
                .homeworkTitle(submission.getHomeworkContent() != null
                        ? submission.getHomeworkContent().getTitle() : null)
                .studentId(student != null ? student.userId() : null)
                .studentName(student != null ? student.fullName() : null)
                .studentEmail(student != null ? student.email() : null)
                .answerContent(submission.getAnswerContent())
                // `fileUrl` cũ (dữ liệu trước V8) vẫn được trả về để tương thích ngược.
                .fileUrl(fileUrl != null ? fileUrl : submission.getFileUrl())
                .score(submission.getScore())
                .feedback(submission.getFeedback())
                .submittedDate(submission.getSubmittedDate())
                .gradedDate(submission.getGradedDate())
                .graded(graded)
                .passed(submission.getPassed())
                .status(resolveStatus(graded))
                .submissionType(submission.getSubmissionType())
                .examAttemptId(submission.getExamAttempt() != null
                        ? submission.getExamAttempt().getAttemptId() : null)
                .filePublicId(submission.getFilePublicId())
                .fileResourceType(submission.getFileResourceType())
                .fileFormat(submission.getFileFormat())
                .fileBytes(submission.getFileBytes())
                .fileOriginalName(submission.getFileOriginalName())
                .gradeHistory(history)
                .build();
    }

    /**
     * Trạng thái bài nộp:
     * {@code not_started} (chưa có bài nộp) | {@code pending} (đã nộp, chờ chấm) | {@code graded}.
     */
    public static String resolveStatus(boolean graded) {
        return graded ? "graded" : "pending";
    }

    /** Map một dòng lịch sử chấm. */
    public static HomeworkGradeHistoryResponse toHistoryResponse(HomeworkGradeHistory h) {
        if (h == null) {
            return null;
        }
        return HomeworkGradeHistoryResponse.builder()
                .historyId(h.getHistoryId())
                .gradedByUserId(h.getGradedBy() != null ? h.getGradedBy().getUserId() : null)
                .gradedByName(h.getGradedBy() != null ? h.getGradedBy().getFullName() : null)
                .oldScore(h.getOldScore())
                .newScore(h.getNewScore())
                .passed(h.getPassed())
                .feedback(h.getFeedback())
                .gradedAt(h.getGradedAt())
                .build();
    }

    /** Gọn tên các field lặp lại. */
    private record UserRef(java.util.UUID userId, String fullName, String email) {
    }
}
