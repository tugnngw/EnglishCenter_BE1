package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.CreateHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.request.GradeHomeworkSubmissionRequest;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;

import java.util.List;
import java.util.UUID;

public interface HomeworkSubmissionService {

    HomeworkSubmissionResponse submitHomework(CreateHomeworkSubmissionRequest request);

    List<HomeworkSubmissionResponse> getMySubmissions();

    HomeworkSubmissionResponse getSubmissionById(UUID submissionId);

    HomeworkSubmissionResponse gradeSubmission(UUID submissionId, GradeHomeworkSubmissionRequest request);
}