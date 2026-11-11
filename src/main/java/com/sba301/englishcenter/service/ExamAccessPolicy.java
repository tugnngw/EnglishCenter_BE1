package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.ExamScope;
import com.sba301.englishcenter.common.enums.ExamStatus;
import com.sba301.englishcenter.common.enums.ExamType;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.util.SessionEnrollmentChecker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Quyết định học viên có được làm một đề hay không (D3-15).
 * <ol>
 *     <li>Đề phải {@code published} và đang trong khung {@code available_from .. available_until};</li>
 *     <li>Đề {@code course}: học viên phải có enrollment của khóa học - hỏi qua
 *     {@link SessionEnrollmentChecker} (gọi EnrollmentService của Dev 4); thi nối tiếp: giữa kỳ cần đạt hết quiz,
 *     cuối kỳ cần đạt giữa kỳ và đạt đủ bài tập của khóa. Bước trước chỉ còn chờ chấm thì báo "đang chờ chấm";</li>
 *     <li>Đề {@code trial} (kiểm tra đầu vào / thi thử) đã bỏ: đề cũ còn trong DB không làm được nữa.</li>
 * </ol>
 * Danh sách đề dùng {@link #session} để mỗi khóa chỉ truy vấn một lần cho cả trang.
 */
@Component
@RequiredArgsConstructor
public class ExamAccessPolicy {

    private final SessionEnrollmentChecker enrollmentChecker;
    /** Luật mở khoá nội dung "Bài thi cuối kỳ" (V29) — video + mentor 1-1. */
    private final CourseExamContentService courseExamContentService;

    @PersistenceContext
    private EntityManager entityManager;

    /** Lý do bị chặn: mã lỗi + câu giải thích có số liệu (VD "Đã đạt 1/3 bài Quiz"). */
    public record Denial(ErrorCode code, String message) {
        static Denial of(ErrorCode code) {
            return new Denial(code, code.getMessage());
        }
    }

    /** Số đề / bài tập của một bước trong khóa: tổng, chưa đạt, chưa đạt nhưng đang chờ chấm. */
    record StepProgress(long total, long unpassed, long pending) {
        long passed() {
            return total - unpassed;
        }

        /** Mọi bài chưa đạt đều đang chờ chấm - học viên không cần làm gì thêm. */
        boolean onlyWaitingForGrading() {
            return unpassed > 0 && pending == unpassed;
        }
    }

    /** @return lỗi chặn truy cập, hoặc empty nếu được phép làm bài. */
    public Optional<ErrorCode> check(UUID userId, Exam exam, LocalDateTime now) {
        return evaluate(userId, exam, now).map(Denial::code);
    }

    public Optional<Denial> evaluate(UUID userId, Exam exam, LocalDateTime now) {
        return session(userId, now).evaluate(exam);
    }

    public void assertCanAttempt(UUID userId, Exam exam, LocalDateTime now) {
        evaluate(userId, exam, now).ifPresent(denial -> {
            throw new AppException(denial.code(), denial.message());
        });
    }

    /** Một lượt đánh giá nhiều đề của cùng học viên - cache kết quả theo khóa học. */
    public Session session(UUID userId, LocalDateTime now) {
        return new Session(userId, now);
    }

    public final class Session {
        private final UUID userId;
        private final LocalDateTime now;
        private final Map<UUID, Boolean> enrolled = new HashMap<>();
        private final Map<String, StepProgress> progress = new HashMap<>();

        private Session(UUID userId, LocalDateTime now) {
            this.userId = userId;
            this.now = now;
        }

        public Optional<Denial> evaluate(Exam exam) {
            if (exam.getStatus() != ExamStatus.PUBLISHED) {
                return Optional.of(Denial.of(ErrorCode.EXAM_NOT_AVAILABLE));
            }
            if (exam.getAvailableFrom() != null && now.isBefore(exam.getAvailableFrom())) {
                return Optional.of(Denial.of(ErrorCode.EXAM_NOT_AVAILABLE));
            }
            if (exam.getAvailableUntil() != null && now.isAfter(exam.getAvailableUntil())) {
                return Optional.of(Denial.of(ErrorCode.EXAM_NOT_AVAILABLE));
            }
            if (userId == null) {
                return Optional.of(Denial.of(ErrorCode.UNAUTHENTICATED));
            }
            if (exam.getExamScope() == ExamScope.COURSE) {
                UUID courseId = exam.getCourse() != null ? exam.getCourse().getCourseId() : null;
                if (courseId == null
                        || !enrolled.computeIfAbsent(courseId, id -> enrollmentChecker.isEnrolled(userId, id))) {
                    return Optional.of(Denial.of(ErrorCode.EXAM_ENROLLMENT_REQUIRED));
                }
                // V29: đề được gắn vào nội dung 'exam' của khóa → áp luật mở khoá
                // (xem xong hết video + mua gói kèm 1-1 nếu có phần tự luận).
                String lockReason = courseExamContentService.findLockReason(exam.getExamId(), userId);
                if ("lessons".equals(lockReason)) {
                    return Optional.of(Denial.of(ErrorCode.EXAM_CONTENT_LOCKED));
                }
                if ("mentor".equals(lockReason)) {
                    return Optional.of(Denial.of(ErrorCode.EXAM_CONTENT_MENTOR_REQUIRED));
                }
                // V29: đề được gắn vào nội dung 'exam' của khóa → luật mở khoá của
                // nội dung này (xem xong hết video + mở khoá mentor 1-1) là luật
                // DUY NHẤT áp dụng → bỏ luật thi nối tiếp (đạt giữa kỳ + đủ bài tập),
                // vì luật thi nối tiếp sẽ chặn ngược lại đề cuối kỳ của khóa.
                if (courseExamContentService.isExamContent(exam)) {
                    return Optional.empty();
                }
                return checkPrerequisites(courseId, exam.getExamType());
            }
            if (exam.getExamScope() == ExamScope.TRIAL) {
                return Optional.of(Denial.of(ErrorCode.EXAM_NOT_AVAILABLE));
            }
            return Optional.empty();
        }

        /**
         * Thi nối tiếp trong một khóa: Giữa kỳ mở khi đã đạt hết Quiz đã công khai của khóa; Cuối kỳ mở khi đã đạt
         * Giữa kỳ đã công khai của khóa và đạt đủ bài tập. Khóa chưa có Quiz / Giữa kỳ thì bỏ qua điều kiện đó.
         */
        private Optional<Denial> checkPrerequisites(UUID courseId, ExamType type) {
            if (type == ExamType.MIDTERM) {
                StepProgress quizzes = examProgress(courseId, ExamType.QUIZ);
                if (quizzes.unpassed() > 0) {
                    return Optional.of(quizzes.onlyWaitingForGrading()
                            ? pending(quizzes.pending() + " bài Quiz")
                            : new Denial(ErrorCode.EXAM_QUIZZES_NOT_PASSED, "Cần đạt hết bài Quiz của khóa trước "
                                    + "khi thi giữa kỳ (đã đạt " + quizzes.passed() + "/" + quizzes.total() + ")"));
                }
            } else if (type == ExamType.FINAL) {
                StepProgress midterms = examProgress(courseId, ExamType.MIDTERM);
                if (midterms.unpassed() > 0) {
                    return Optional.of(midterms.onlyWaitingForGrading()
                            ? pending("bài thi giữa kỳ")
                            : Denial.of(ErrorCode.EXAM_MIDTERM_NOT_PASSED));
                }
                StepProgress homework = progress.computeIfAbsent(courseId + "|homework",
                        key -> homeworkProgress(userId, courseId));
                if (homework.unpassed() > 0) {
                    return Optional.of(homework.onlyWaitingForGrading()
                            ? pending(homework.pending() + " bài tập")
                            : new Denial(ErrorCode.EXAM_COURSE_NOT_COMPLETED, "Cần đạt đủ bài tập của khóa trước "
                                    + "khi thi cuối kỳ (đã đạt " + homework.passed() + "/" + homework.total() + ")"));
                }
            }
            return Optional.empty();
        }

        private StepProgress examProgress(UUID courseId, ExamType type) {
            return progress.computeIfAbsent(courseId + "|" + type.getValue(),
                    key -> ExamAccessPolicy.this.examProgress(userId, courseId, type));
        }

        private Denial pending(String what) {
            return new Denial(ErrorCode.EXAM_PREREQUISITE_PENDING_GRADING,
                    "Đang chờ mentor chấm " + what + "; đề sẽ mở khi bài được chấm đạt");
        }
    }

    /**
     * Cảnh báo cho admin khi bộ đề của khóa chưa đủ, làm luật thi nối tiếp bị bỏ qua
     * (VD có Cuối kỳ mà chưa có Giữa kỳ đã công khai → học viên vào thẳng Cuối kỳ).
     */
    public List<String> courseSetupWarnings(Exam exam) {
        List<String> warnings = new ArrayList<>();
        if (exam.getExamScope() != ExamScope.COURSE || exam.getCourse() == null) {
            return warnings;
        }
        UUID courseId = exam.getCourse().getCourseId();
        if (exam.getExamType() == ExamType.MIDTERM && countPublished(courseId, ExamType.QUIZ) == 0) {
            warnings.add("Khóa chưa có đề Quiz nào đã công khai: học viên vào thẳng đề Giữa kỳ");
        }
        if (exam.getExamType() == ExamType.FINAL) {
            if (countPublished(courseId, ExamType.MIDTERM) == 0) {
                warnings.add("Khóa chưa có đề Giữa kỳ nào đã công khai: học viên chỉ cần đạt bài tập là thi "
                        + "được Cuối kỳ");
            }
            if (homeworkProgress(new UUID(0L, 0L), courseId).total() == 0) {
                warnings.add("Khóa chưa có bài tập nào: điều kiện đạt đủ bài tập trước Cuối kỳ bị bỏ qua");
            }
        }
        return warnings;
    }

    long countPublished(UUID courseId, ExamType type) {
        Number count = (Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM exams e WHERE e.course_id = :courseId "
                                + "AND CAST(e.exam_type AS text) = :examType AND CAST(e.status AS text) = 'published'")
                .setParameter("courseId", courseId)
                .setParameter("examType", type.getValue())
                .getSingleResult();
        return count == null ? 0 : count.longValue();
    }

    /** Đề đã công khai cùng loại trong khóa: tổng / học viên chưa có lượt đạt / trong đó có lượt đang chờ chấm. */
    StepProgress examProgress(UUID userId, UUID courseId, ExamType type) {
        Object[] row = (Object[]) entityManager.createNativeQuery(
                        "SELECT COUNT(*), "
                                + "COUNT(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM exam_attempts a "
                                + "  WHERE a.exam_id = e.exam_id AND a.student_id = :studentId AND a.passed = TRUE)), "
                                + "COUNT(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM exam_attempts a "
                                + "  WHERE a.exam_id = e.exam_id AND a.student_id = :studentId AND a.passed = TRUE) "
                                + "  AND EXISTS (SELECT 1 FROM exam_attempts a WHERE a.exam_id = e.exam_id "
                                + "  AND a.student_id = :studentId AND CAST(a.status AS text) = 'submitted')) "
                                + "FROM exams e WHERE e.course_id = :courseId "
                                + "AND CAST(e.exam_type AS text) = :examType AND CAST(e.status AS text) = 'published'")
                .setParameter("courseId", courseId)
                .setParameter("examType", type.getValue())
                .setParameter("studentId", userId)
                .getSingleResult();
        return toProgress(row);
    }

    /**
     * Bài tập (course_content loại homework) của khóa: tổng / chưa có bài nộp đạt ({@code passed = true}) /
     * trong đó đã nộp nhưng chưa chấm ({@code passed IS NULL}). Đọc bảng của module khóa học bằng native query.
     */
    StepProgress homeworkProgress(UUID userId, UUID courseId) {
        Object[] row = (Object[]) entityManager.createNativeQuery(
                        "SELECT COUNT(*), "
                                + "COUNT(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM homework_submissions s "
                                + "  WHERE s.homework_content_id = c.content_id AND s.student_id = :studentId "
                                + "  AND s.passed = TRUE)), "
                                + "COUNT(*) FILTER (WHERE NOT EXISTS (SELECT 1 FROM homework_submissions s "
                                + "  WHERE s.homework_content_id = c.content_id AND s.student_id = :studentId "
                                + "  AND s.passed = TRUE) "
                                + "  AND EXISTS (SELECT 1 FROM homework_submissions s "
                                + "  WHERE s.homework_content_id = c.content_id AND s.student_id = :studentId "
                                + "  AND s.passed IS NULL)) "
                                + "FROM course_content c WHERE c.course_id = :courseId AND c.content_type = 'homework'")
                .setParameter("courseId", courseId)
                .setParameter("studentId", userId)
                .getSingleResult();
        return toProgress(row);
    }

    private static StepProgress toProgress(Object[] row) {
        return new StepProgress(asLong(row[0]), asLong(row[1]), asLong(row[2]));
    }

    private static long asLong(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }
}
