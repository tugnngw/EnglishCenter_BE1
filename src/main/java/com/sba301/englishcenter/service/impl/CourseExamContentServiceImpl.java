package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.common.enums.QuestionType;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.Exam;
import com.sba301.englishcenter.entity.ExamQuestion;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.ExamQuestionRepository;
import com.sba301.englishcenter.service.CourseExamContentService;
import com.sba301.englishcenter.service.HomeworkGraderAssignmentService;
import com.sba301.englishcenter.service.LessonProgressService;
import com.sba301.englishcenter.util.JsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Triển khai {@link CourseExamContentService}.
 */
@Service
@RequiredArgsConstructor
public class CourseExamContentServiceImpl implements CourseExamContentService {

    private final CourseContentRepository courseContentRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final LessonProgressService lessonProgressService;
    private final HomeworkGraderAssignmentService graderAssignmentService;

    @Override
    @Transactional(readOnly = true)
    public long countLessons(UUID courseId) {
        if (courseId == null) {
            return 0;
        }
        return lessonIdsWithVideo(courseId).size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> lessonIdsWithVideo(UUID courseId) {
        List<UUID> ids = new ArrayList<>();
        for (CourseContent content
                : courseContentRepository
                .findByCourse_CourseIdAndContentTypeOrderByContentOrderAsc(courseId, ContentType.LESSON)) {
            String videoUrl = JsonUtil.readString(content.getContentData(), "videoUrl");
            if (videoUrl != null && !videoUrl.isBlank()) {
                ids.add(content.getContentId());
            }
        }
        return ids;
    }

    @Override
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

    @Override
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

    @Override
    @Transactional(readOnly = true)
    public boolean hasMentorPlan(UUID studentId) {
        return studentId != null && graderAssignmentService.hasMentor(studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public UUID findCourseIdOfExamContent(UUID examId) {
        if (examId == null) {
            return null;
        }
        List<CourseContent> contents =
                courseContentRepository.findByContentTypeAndExam_ExamId(ContentType.EXAM, examId);
        for (CourseContent content : contents) {
            if (content.getCourse() != null) {
                return content.getCourse().getCourseId();
            }
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isExamContent(Exam exam) {
        return exam != null && findCourseIdOfExamContent(exam.getExamId()) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public String findLockReason(UUID examId, UUID studentId) {
        UUID courseId = findCourseIdOfExamContent(examId);
        if (courseId == null) {
            return null; // đề không gắn vào nội dung 'exam' → áp luật thi nối tiếp cũ
        }
        if (!allLessonsCompleted(studentId, courseId)) {
            return "lessons";
        }
        return null;
    }
}
