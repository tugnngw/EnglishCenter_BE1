package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Quy tắc mở khoá nội dung <b>Bài thi cuối kỳ</b> ({@code content_type = 'exam'}) — V29.
 * <p>
 * Luật nghiệp vụ (chốt):
 * <ol>
 *   <li>Bài thi cuối kỳ <b>KHOÁ</b> cho tới khi học viên đã xem xong <b>TẤT CẢ</b> bài học
 *       (video) của khóa học.</li>
 *   <li>Đề chỉ có câu <b>trắc nghiệm / tự động chấm</b> → học viên làm được ngay.</li>
 *   <li>Đề có câu <b>tự luận</b> (Writing / Speaking — loại {@code essay}) → học viên phải
 *       <b>mở khoá mentor</b> (đã mua gói kèm 1-1) để mentor chấm tay.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class CourseExamContentService {

    private final CourseContentRepository courseContentRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final LessonProgressService lessonProgressService;
    private final HomeworkGraderAssignmentService graderAssignmentService;

    /**
     * Số bài học (video) của khóa học — dùng làm mẫu số của điều kiện "đã học hết".
     * <p>
     * Chỉ tính bài học CÓ VIDEO ({@code content_data.videoUrl} không rỗng): bài học
     * không có video thì học viên không thể xem → không thể hoàn thành → nếu tính vào
     * mẫu số thì bài thi cuối kỳ bị khoá vĩnh viễn.
     */
    @Transactional(readOnly = true)
    public long countLessons(UUID courseId) {
        if (courseId == null) {
            return 0;
        }
        return lessonIdsWithVideo(courseId).size();
    }

    /** Id các bài học có video của khóa học. */
    @Transactional(readOnly = true)
    public List<UUID> lessonIdsWithVideo(UUID courseId) {
        List<UUID> ids = new java.util.ArrayList<>();
        for (com.sba301.englishcenter.entity.CourseContent content
                : courseContentRepository
                .findByCourse_CourseIdAndContentTypeOrderByContentOrderAsc(courseId, ContentType.LESSON)) {
            String videoUrl = com.sba301.englishcenter.util.JsonUtil
                    .readString(content.getContentData(), "videoUrl");
            if (videoUrl != null && !videoUrl.isBlank()) {
                ids.add(content.getContentId());
            }
        }
        return ids;
    }

    /**
     * Học viên đã xem xong TẤT CẢ video của khóa chưa?
     * <p>
     * Khóa chưa có bài học video nào → coi như đã xong (không chặn vô ích).
     */
    @Transactional(readOnly = true)
    public boolean allLessonsCompleted(UUID studentId, UUID courseId) {
        List<UUID> lessonIds = lessonIdsWithVideo(courseId);
        if (lessonIds.isEmpty()) {
            return true;
        }
        if (studentId == null) {
            return false;
        }
        long completed = lessonProgressService.countCompletedLessons(studentId, courseId, lessonIds);
        return completed >= lessonIds.size();
    }

    /**
     * Đề thi này có câu tự luận (cần mentor chấm tay) hay không?
     */
    @Transactional(readOnly = true)
    public boolean requiresMentorGrading(UUID examId) {
        if (examId == null) {
            return false;
        }
        List<ExamQuestion> questions = examQuestionRepository.findDetailedByExamId(examId);
        return questions.stream()
                .anyMatch(eq -> eq.getQuestion() != null
                        && eq.getQuestion().getQuestionType() == QuestionType.ESSAY);
    }

    /** Học viên đã mua gói kèm 1-1 (có mentor chấm tay) hay chưa. */
    @Transactional(readOnly = true)
    public boolean hasMentorPlan(UUID studentId) {
        return studentId != null && graderAssignmentService.hasMentor(studentId);
    }

    /**
     * Đề thi có được gắn vào khóa học (dưới dạng nội dung 'exam') không?
     * <p>
     * Nếu có → luật mở khoá của nội dung 'exam' áp dụng cho đề đó, kể cả khi học viên
     * mở đề từ Ngân hàng đề (để không lách luật).
     */
    @Transactional(readOnly = true)
    public UUID findCourseIdOfExamContent(UUID examId) {
        if (examId == null) {
            return null;
        }
        List<com.sba301.englishcenter.entity.CourseContent> contents =
                courseContentRepository.findByContentTypeAndExam_ExamId(ContentType.EXAM, examId);
        for (com.sba301.englishcenter.entity.CourseContent content : contents) {
            if (content.getCourse() != null) {
                return content.getCourse().getCourseId();
            }
        }
        return null;
    }

    /** Đề có gắn vào nội dung 'exam' của khóa nào không. */
    @Transactional(readOnly = true)
    public boolean isExamContent(Exam exam) {
        return exam != null && findCourseIdOfExamContent(exam.getExamId()) != null;
    }

    /**
     * Lý do học viên CHƯA được làm đề cuối kỳ (null = được phép).
     * <p>
     * Chỉ áp dụng cho đề đã được gắn vào khóa học dưới dạng nội dung 'exam'.
     * Kết quả:
     * <ul>
     *   <li>{@code "lessons"} — chưa xem xong TẤT CẢ video của khóa;</li>
     *   <li>{@code "mentor"} — đề có phần tự luận nhưng học viên chưa mua gói kèm 1-1.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public String findLockReason(UUID examId, UUID studentId) {
        UUID courseId = findCourseIdOfExamContent(examId);
        if (courseId == null) {
            return null; // đề không gắn vào nội dung 'exam' → áp luật thi nối tiếp cũ
        }
        if (!allLessonsCompleted(studentId, courseId)) {
            return "lessons";
        }
        if (requiresMentorGrading(examId) && !hasMentorPlan(studentId)) {
            return "mentor";
        }
        return null;
    }
}
