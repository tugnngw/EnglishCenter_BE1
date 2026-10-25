package com.sba301.englishcenter.service;

import com.sba301.englishcenter.common.PageResponse;
import com.sba301.englishcenter.dto.request.LearningPathRequest;
import com.sba301.englishcenter.dto.response.LearningPathProgressResponse;
import com.sba301.englishcenter.dto.response.LearningPathResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Lộ trình học theo chứng chỉ (TASK-D5-03).
 */
public interface LearningPathService {

    PageResponse<LearningPathResponse> getLearningPaths(UUID certificateId, Pageable pageable);

    LearningPathResponse getLearningPath(UUID learningPathId);

    LearningPathResponse create(UUID adminId, LearningPathRequest request);

    LearningPathResponse update(UUID learningPathId, LearningPathRequest request);

    /** Không xóa được khi đã có học viên ghi danh. */
    void delete(UUID learningPathId);

    /** Ghi danh (gọi lại nhiều lần vẫn chỉ 1 bản ghi), trả về tiến độ hiện tại. */
    LearningPathProgressResponse enroll(UUID studentId, UUID learningPathId);

    /** Tiến độ mọi lộ trình đã ghi danh, tính lại theo dữ liệu thật rồi lưu. */
    List<LearningPathProgressResponse> getMyProgress(UUID studentId);
}
