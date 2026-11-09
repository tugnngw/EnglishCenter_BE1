-- =====================================================
-- Migration V27: Dev 5 - Bài tập của mentor (tận dụng homework của Dev 2)
-- Bài tập mentor tạo (import PDF) là 1 dòng course_content loại 'homework' KHÔNG thuộc khóa học nào:
--   course_id = NULL, owner_mentor_id = mentor tạo. Mentor gắn bài vào lộ trình của mình (chặng HOMEWORK),
--   chỉ học viên đã mua gói kèm 1-1 + ghi danh lộ trình mới làm bài; mentor 1-1 chấm tay (không AI chấm),
--   bài nộp / rubric dùng lại nguyên của Dev 2.
-- Hạn nộp: content_data.dueDays = số ngày kể từ khi học viên ghi danh lộ trình chứa bài.
-- CHỈ thêm mới / nới ràng buộc, không sửa migration cũ; dữ liệu khóa học cũ không đổi.
-- =====================================================

ALTER TABLE course_content ALTER COLUMN course_id DROP NOT NULL;

ALTER TABLE course_content
    ADD COLUMN IF NOT EXISTS owner_mentor_id UUID REFERENCES users(user_id) ON DELETE CASCADE;

-- Nội dung không thuộc khóa học thì bắt buộc là bài tập của 1 mentor
ALTER TABLE course_content
    ADD CONSTRAINT chk_course_content_owner
        CHECK (course_id IS NOT NULL OR (content_type = 'homework' AND owner_mentor_id IS NOT NULL));

CREATE INDEX IF NOT EXISTS idx_course_content_owner_mentor
    ON course_content(owner_mentor_id, created_at DESC) WHERE owner_mentor_id IS NOT NULL;
