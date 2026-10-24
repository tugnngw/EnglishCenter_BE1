package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.common.enums.ContentType;
import com.sba301.englishcenter.dto.request.CreateHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.request.GradeHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;
import com.sba301.englishcenter.entity.CourseContent;
import com.sba301.englishcenter.entity.HomeworkSubmission;
import com.sba301.englishcenter.entity.User;
import com.sba301.englishcenter.exception.AppException;
import com.sba301.englishcenter.exception.ErrorCode;
import com.sba301.englishcenter.repository.CourseContentRepository;
import com.sba301.englishcenter.repository.HomeworkSubmissionRepository;
import com.sba301.englishcenter.repository.UserRepository;
import com.sba301.englishcenter.security.SecurityUtils;
import com.sba301.englishcenter.service.HomeworkSubmissionService;
import com.sba301.englishcenter.util.SessionVariableHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkSubmissionServiceImpl implements HomeworkSubmissionService {

    private final HomeworkSubmissionRepository submissionRepository;
    private final CourseContentRepository courseContentRepository;
    private final UserRepository userRepository;
    private final SessionVariableHelper sessionVariableHelper;
    private final com.sba301.englishcenter.service.EnrollmentService enrollments;

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
                    "Nội dung trỏ tới không phải bài tập (homework)");
        }

        if (!enrollments.isEnrolled(studentId, homework.getCourse().getCourseId())) {
            throw new AppException(ErrorCode.ENROLLMENT_REQUIRED);
        }
        sessionVariableHelper.setCurrentUserId(studentId);

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        HomeworkSubmission submission = HomeworkSubmission.builder()
                .homeworkContent(homework)
                .student(student)
                .answerContent(request.getAnswerContent())
                .fileUrl(request.getFileUrl())
                .submittedDate(LocalDateTime.now())
                .build();

        submission = submissionRepository.save(submission);
        log.info("Học viên {} nộp bài tập {} -> {}", studentId, homework.getContentId(), submission.getSubmissionId());

        return toSubmissionResponse(submission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HomeworkSubmissionResponse> getMySubmissions() {
        UUID studentId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));

        return submissionRepository.findByStudent_UserIdOrderBySubmittedDateDesc(studentId).stream()
                .map(this::toSubmissionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HomeworkSubmissionResponse getSubmissionById(UUID submissionId) {
        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND));
        return toSubmissionResponse(submission);
    }

    @Override
    @Transactional
    public HomeworkSubmissionResponse gradeSubmission(UUID submissionId, GradeHomeworkSubmissionRequest request) {
        UUID graderId = SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHENTICATED));
        sessionVariableHelper.setCurrentUserId(graderId);

        HomeworkSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new AppException(ErrorCode.HOMEWORK_SUBMISSION_NOT_FOUND));

        submission.setScore(request.getScore());
        submission.setFeedback(request.getFeedback());
        submission.setGradedDate(LocalDateTime.now());

        submission = submissionRepository.save(submission);
        log.info("Đã chấm điểm submission {}: score={}", submissionId, request.getScore());

        return toSubmissionResponse(submission);
    }

    private HomeworkSubmissionResponse toSubmissionResponse(HomeworkSubmission submission) {
        User student = submission.getStudent();
        CourseContent homework = submission.getHomeworkContent();

        boolean graded = submission.getGradedDate() != null;

        return HomeworkSubmissionResponse.builder()
                .submissionId(submission.getSubmissionId())
                .homeworkContentId(homework != null ? homework.getContentId() : null)
                .homeworkTitle(homework != null ? homework.getTitle() : null)
                .studentId(student != null ? student.getUserId() : null)
                .studentName(student != null ? student.getFullName() : null)
                .studentEmail(student != null ? student.getEmail() : null)
                .answerContent(submission.getAnswerContent())
                .fileUrl(submission.getFileUrl())
                .score(submission.getScore())
                .feedback(submission.getFeedback())
                .submittedDate(submission.getSubmittedDate())
                .gradedDate(submission.getGradedDate())
                .graded(graded)
                .build();
    }
}
