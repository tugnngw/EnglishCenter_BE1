package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.response.StudentProgressResponse;

import java.util.UUID;

/**
 * Mentor theo dõi tiến độ học tập của học viên mình phụ trách (Dev 5).
 * "Phụ trách" = mentor đã nhận yêu cầu tư vấn của học viên hoặc đã tạo gói kèm 1-1 cho học viên.
 */
public interface StudentProgressService {

    /** Mentor không phụ trách học viên -> UNAUTHORIZED (403); không phải người học -> USER_NOT_FOUND. */
    StudentProgressResponse getProgress(UUID mentorId, UUID studentId);
}
