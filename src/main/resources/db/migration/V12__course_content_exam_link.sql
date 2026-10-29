-- =====================================================
-- Migration V7: Liên kết khóa ngoại cho nội dung khóa học
--
-- Mục tiêu:
--   1. `course_content.exam_id` — FK tới `exams.exam_id` cho nội dung loại
--      'homework' (bài tập trắc nghiệm). Trước đây chỉ lưu `examId` trong
--      JSONB `content_data` → không có ràng buộc FK, không thể truy vấn ngược.
--   2. Không đụng `content_type_enum`: giữ nguyên 3 giá trị
--      lesson / vocabulary / homework.
--
-- Không sửa/xoá migration cũ. Chỉ thêm cột + index.
-- =====================================================

-- 1. Cột FK tới bảng exams
ALTER TABLE course_content
    ADD COLUMN IF NOT EXISTS exam_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_course_content_exam'
          AND conrelid = 'course_content'::regclass
    ) THEN
        ALTER TABLE course_content
            ADD CONSTRAINT fk_course_content_exam
                FOREIGN KEY (exam_id) REFERENCES exams(exam_id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_course_content_exam
    ON course_content(exam_id);

COMMENT ON COLUMN course_content.exam_id IS
    'Đề thi liên kết cho nội dung homework (content_type_enum = homework). ON DELETE SET NULL để xoá đề thi không làm hỏng nội dung khóa học.';
