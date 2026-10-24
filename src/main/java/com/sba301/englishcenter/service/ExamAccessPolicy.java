package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.util.SessionEnrollmentChecker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Quyết định học viên có được làm một đề hay không (D3-15).
 * <ol>
 *     <li>Đề phải {@code published} và đang trong khung {@code available_from .. available_until};</li>
 *     <li>Đề {@code course}: học viên phải có enrollment của khóa học - hỏi qua
 *     {@link SessionEnrollmentChecker} (gọi EnrollmentService của Dev 4);</li>
 *     <li>Đề {@code trial} trả phí ({@code is_free = false}): cần giao dịch {@code trial_exam_access}
 *     thành công trong {@code payment_transactions} (bảng của Dev 4, chỉ đọc bằng native query nên không
 *     tạo entity trùng với Dev 4).</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class ExamAccessPolicy {

    private final SessionEnrollmentChecker enrollmentChecker;

    @PersistenceContext
    private EntityManager entityManager;

    /** @return lỗi chặn truy cập, hoặc empty nếu được phép làm bài. */
    public Optional<ErrorCode> check(UUID userId, Exam exam, LocalDateTime now) {
        if (exam.getStatus() != ExamStatus.PUBLISHED) {
            return Optional.of(ErrorCode.EXAM_NOT_AVAILABLE);
        }
        if (exam.getAvailableFrom() != null && now.isBefore(exam.getAvailableFrom())) {
            return Optional.of(ErrorCode.EXAM_NOT_AVAILABLE);
        }
        if (exam.getAvailableUntil() != null && now.isAfter(exam.getAvailableUntil())) {
            return Optional.of(ErrorCode.EXAM_NOT_AVAILABLE);
        }
        if (userId == null) {
            return Optional.of(ErrorCode.UNAUTHENTICATED);
        }
        if (exam.getExamScope() == ExamScope.COURSE) {
            UUID courseId = exam.getCourse() != null ? exam.getCourse().getCourseId() : null;
            if (courseId == null || !enrollmentChecker.isEnrolled(userId, courseId)) {
                return Optional.of(ErrorCode.EXAM_ENROLLMENT_REQUIRED);
            }
        } else if (Boolean.FALSE.equals(exam.getIsFree()) && !hasPaidTrialAccess(userId, exam.getExamId())) {
            return Optional.of(ErrorCode.EXAM_PAYMENT_REQUIRED);
        }
        return Optional.empty();
    }

    public void assertCanAttempt(UUID userId, Exam exam, LocalDateTime now) {
        check(userId, exam, now).ifPresent(errorCode -> {
            throw new AppException(errorCode);
        });
    }

    boolean hasPaidTrialAccess(UUID userId, UUID examId) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM payment_transactions "
                                + "WHERE student_id = :studentId AND exam_id = :examId "
                                + "AND transaction_type = 'trial_exam_access' AND transaction_status = 'success'")
                .setParameter("studentId", userId)
                .setParameter("examId", examId)
                .getSingleResult();
        return count != null && count.longValue() > 0;
    }
}
