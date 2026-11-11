package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.AnswerItemRequest;
import com.sba301.englishcenter.dto.response.AttemptDetailResponse;
import com.sba301.englishcenter.dto.response.AttemptResultResponse;
import com.sba301.englishcenter.dto.response.HomeworkSubmissionResponse;

import java.util.List;
import java.util.UUID;

/**
 * Luồng làm bài TRẮC NGHIỆM cho một bài tập (Dev 2.5).
 * <p>
 * Bài tập trắc nghiệm dùng chung máy chấm {@link com.sba301.englishcenter.service.ExamGrader}
 * của module Exam, nhưng có LUÔNG RIÊNG:
 * <ol>
 *     <li>{@link #startAttempt} — mở lượt thi thật cho đề mà bài tập trỏ tới;</li>
 *     <li>{@link #submitAttempt} — nộp lượt thi (chấm tự động) rồi TỰ TẠO bản ghi
 *         {@code homework_submissions} gắn với lượt thi đó.</li>
 * </ol>
 * <p>
 * Nhờ vậy học viên làm bài ngay trong bài tập (không phải rời sang phòng thi của
 * module Exam) và bài nộp luôn gắn đúng lượt thi đã nộp.
 * <p>
 * <b>Không sửa code của module Exam</b> — service này chỉ GỌI vào
 * {@link ExamAttemptService} (interface công khai của team).
 */
public interface HomeworkExamService {

    /**
     * Mở lượt thi cho bài tập trắc nghiệm.
     * <p>
     * Nếu bài tập đã có lượt thi đang mở → trả lại lượt đó (không tạo trùng).
     */
    AttemptDetailResponse startAttempt(UUID homeworkContentId);

    /**
     * Tải lượt thi (câu hỏi + đáp án đã lưu) để học viên tiếp tục làm bài.
     */
    AttemptDetailResponse getAttempt(UUID attemptId);

    /**
     * Nộp bài trắc nghiệm: chấm tự động rồi tạo bài nộp.
     * <p>
     * Bài nộp được tạo bởi chính service này nên điểm và {@code passed} luôn
     * khớp với lượt thi vừa nộp.
     *
     * @return kết quả lượt thi kèm bài nộp đã tạo
     */
    AttemptResultResponse submitAttempt(UUID attemptId, List<AnswerItemRequest> answers);

    /**
     * Tạo bài nộp từ một lượt thi đã nộp.
     * <p>
     * Idempotent: nếu lượt thi này đã có bài nộp → trả bài nộp cũ, tránh
     * tạo trùng khi FE gọi lại.
     */
    HomeworkSubmissionResponse createSubmissionFromAttempt(UUID attemptId);
}
