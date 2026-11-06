package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.CreateHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.request.GradeHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;

import java.util.List;
import java.util.UUID;

public interface HomeworkSubmissionService {

    HomeworkSubmissionResponse submitHomework(CreateHomeworkSubmissionRequest request);

    /**
     * Kiểm tra học viên còn được làm / nộp bài tập không (hạn nộp + số lần làm tối đa).
     * Dùng chung cho nộp file và mở lượt thi trắc nghiệm.
     */
    void assertCanAttempt(com.sba301.englishcenter.entity.CourseContent homework, UUID studentId);

    List<HomeworkSubmissionResponse> getMySubmissions();

    HomeworkSubmissionResponse getSubmissionById(UUID submissionId);

    HomeworkSubmissionResponse gradeSubmission(UUID submissionId, GradeHomeworkSubmissionRequest request);
}