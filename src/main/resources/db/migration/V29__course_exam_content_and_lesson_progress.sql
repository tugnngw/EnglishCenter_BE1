-- =====================================================
-- Migration V29: Dev 2 - Nội dung "Bài thi cuối kỳ" + tiến độ xem video
--   1. Thêm giá trị 'exam' vào enum content_type_enum (nội dung trỏ tới đề cuối kỳ)
--   2. Bảng lesson_progress: học viên đã xem hết video bài học chưa
--      → dùng để mở khoá bài thi cuối kỳ sau khi học hết TẤT CẢ video của khóa.
-- CHỈ thêm mới, không sửa/xóa dữ liệu hay migration cũ.
-- =====================================================

-- 1. ENUM content_type_enum: thêm 'exam'
--    Giá trị mới chưa dùng được trong cùng transaction với DML → dùng ADD VALUE rồi COMMIT ngầm.
ALTER TYPE content_type_enum ADD VALUE IF NOT EXISTS 'exam';

-- 2. Bảng tiến độ bài học (video)
--    Khoá chính COMPOSITE (content_id, student_id): mỗi học viên có một dòng
--    tiến độ riêng cho mỗi bài học. (Khoá chỉ theo content_id sẽ khiến học viên
--    thứ 2 học cùng bài bị đụng khoá / ghi đè tiến độ của người trước.)
CREATE TABLE IF NOT EXISTS lesson_progress (
    content_id      UUID NOT NULL,
    student_id      UUID NOT NULL,
    -- Giây đã xem tối đa (chống tua): lưu mốc thời gian đã xem, không phải currentTime hiện tại.
    watched_seconds INT    NOT NULL DEFAULT 0,
    -- Tổng thời lượng video (giây) — dùng để biết đã xem hết hay chưa.
    duration_seconds INT   NOT NULL DEFAULT 0,
    completed       BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at    TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_updated    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_lesson_progress PRIMARY KEY (content_id, student_id),
    CONSTRAINT fk_lesson_progress_content FOREIGN KEY (content_id)
        REFERENCES course_content (content_id) ON DELETE CASCADE,
    CONSTRAINT fk_lesson_progress_student FOREIGN KEY (student_id)
        REFERENCES users (user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_student
    ON lesson_progress (student_id);

COMMENT ON TABLE lesson_progress IS
    'Tiến độ học viên với bài học (video): mốc giây đã xem tối đa + cờ đã xem xong';
COMMENT ON COLUMN lesson_progress.watched_seconds IS
    'Mốc giây đã xem tối đa — dùng để chặn tua video (client không được nhảy quá mốc này)';
