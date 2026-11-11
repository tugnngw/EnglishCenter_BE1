package com.sba301.englishcenter.service;

import java.util.UUID;

/**
 * Chấm bài tập bằng AI cho học viên KHÔNG mua mentor 1-1.
 * <p>
 * Áp dụng cho bài tự luận ({@code text}), bài nộp file ({@code file}) và bài ghi âm
 * ({@code audio}). Bài trắc nghiệm vốn đã tự chấm từ lượt thi nên không đi qua đây.
 * <p>
 * <b>Thang điểm:</b> AI chấm theo thang chứng chỉ (IELTS 0-9, TOEIC 0-200…) rồi hệ
 * thống QUY ĐỔI về thang của bài tập ({@code content_data.maxScore}) để học viên thấy
 * đúng thang đã cấu hình, và tính {@code passed} theo {@code passingScore} của bài tập.
 * <p>
 * AI chấm mất 10 - 60 giây → gọi AI <b>ngoài transaction</b> (không giữ khoá dòng
 * trong lúc chờ), giống {@code ExamAttemptServiceImpl.aiGradeAttempt}.
 */
public interface HomeworkAiGradingService {

    /**
     * Chấm bài nộp bằng AI (dùng khi học viên không mua mentor 1-1).
     * <p>
     * Trả về {@code true} nếu đã chấm xong, {@code false} nếu bài này có mentor được
     * giao chấm tay (hoặc đã chấm rồi) → không đụng vào.
     *
     * @param submissionId bài nộp cần chấm
     * @param studentId    học viên (dùng để xác định thang điểm chứng chỉ)
     */
    boolean gradeByAi(UUID submissionId, UUID studentId);
}
