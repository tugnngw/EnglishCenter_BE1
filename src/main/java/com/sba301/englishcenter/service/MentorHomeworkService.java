package com.sba301.englishcenter.service;

import com.sba301.englishcenter.dto.request.HomeworkAiImportRequest;
import com.sba301.englishcenter.dto.request.MentorHomeworkRequest;
import com.sba301.englishcenter.dto.response.CourseContentResponse;
import com.sba301.englishcenter.dto.response.HomeworkAiDraftResponse;
import com.sba301.englishcenter.dto.response.MentorHomeworkResponse;
import com.sba301.englishcenter.entity.CourseContent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Bài tập riêng của mentor (Dev 5, V27) - tận dụng homework của Dev 2:
 * bài tập là 1 dòng course_content loại homework với course_id = NULL, owner_mentor_id = mentor.
 * Mentor gắn bài vào lộ trình của mình (chặng HOMEWORK). Chỉ học viên ĐÃ MUA gói kèm 1-1 và ghi danh lộ trình
 * mới làm bài; mentor 1-1 của học viên chấm tay (không AI chấm). Nộp bài / rubric dùng lại luồng homework của Dev 2.
 */
public interface MentorHomeworkService {

    // ===================== mentor =====================

    /** Bài tập của mentor, mới nhất trước (kèm lộ trình đang chứa bài + số bài nộp). */
    List<MentorHomeworkResponse> getMine(UUID mentorId);

    MentorHomeworkResponse create(UUID mentorId, MentorHomeworkRequest request);

    /** Đã có bài nộp thì không đổi hình thức nộp / điểm tối đa (giống homework khóa học). */
    MentorHomeworkResponse update(UUID mentorId, UUID contentId, MentorHomeworkRequest request);

    /** Chỉ xóa được bài chưa có bài nộp và không nằm trong lộ trình nào. */
    void delete(UUID mentorId, UUID contentId);

    /** AI (Claude) đọc PDF đề bài, trả bản nháp để mentor chỉnh rồi lưu. */
    HomeworkAiDraftResponse importFromPdf(HomeworkAiImportRequest request);

    // ===================== học viên =====================

    /**
     * Học viên mở bài tập từ lộ trình: phải ghi danh lộ trình chứa bài.
     * Trả về cùng dạng nội dung khóa học (FE dùng lại modal nộp bài của Dev 2),
     * content_data có thêm dueDate (yyyy-MM-dd) tính từ ngày ghi danh + dueDays.
     */
    CourseContentResponse getForStudent(UUID studentId, UUID contentId);

    // ===================== dùng chung cho module khác =====================

    /** Bài tập riêng của mentor (không thuộc khóa học) hay không. */
    boolean isMentorHomework(CourseContent content);

    /** Học viên đã mua gói kèm 1-1 (đã thanh toán) và ghi danh lộ trình chứa bài chưa, chưa thì báo lỗi. */
    void assertStudentCanAccess(UUID studentId, CourseContent homework);

    /** Hạn nộp của học viên này = ngày ghi danh lộ trình (giờ VN) + dueDays; null = không có hạn. */
    LocalDate resolveDueDate(CourseContent homework, UUID studentId);

    /** Lộ trình: tiêu đề bài tập, bài phải là bài tập của chính người tạo lộ trình. */
    String resolveTitleForPath(UUID contentId, UUID pathOwnerId);

    /** Lộ trình: chặng HOMEWORK hoàn thành khi học viên có bài nộp được chấm ĐẠT. */
    boolean hasPassed(UUID studentId, UUID contentId);
}
