-- =====================================================
-- Migration V23: Chấm bài tập tự động bằng AI + gán mentor 1-1
--
-- Mục tiêu (chỉ CÔNG THÊM — không xoá/đổi kiểu cột cũ):
--   1. `homework_submissions.graded_by_type` — ai chấm: 'ai' | 'mentor' | NULL (chưa chấm).
--   2. `homework_submissions.assigned_grader_id` — mentor được giao chấm bài này
--      (mentor 1-1 của học viên). NULL = không có mentor → AI chấm tự động.
--   3. `homework_grade_history.graded_by_type`  — ai chấm ở từng lần chấm/sửa điểm.
--
-- Tương thích ngược: mọi cột mới đều NULLABLE, dữ liệu cũ chạy y hệt.
-- =====================================================

-- ---------------------------------------------------------------
-- 1. Ai chấm + mentor được giao chấm
-- ---------------------------------------------------------------
ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS graded_by_type VARCHAR(20);

ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS assigned_grader_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_homework_sub_assigned_grader'
          AND conrelid = 'homework_submissions'::regclass
    ) THEN
        ALTER TABLE homework_submissions
            ADD CONSTRAINT fk_homework_sub_assigned_grader
                FOREIGN KEY (assigned_grader_id) REFERENCES users(user_id) ON DELETE SET NULL;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_homework_sub_assigned_grader
    ON homework_submissions(assigned_grader_id);

CREATE INDEX IF NOT EXISTS idx_homework_sub_graded_by_type
    ON homework_submissions(graded_by_type);

-- ---------------------------------------------------------------
-- 2. Lịch sử chấm: ghi lại ai chấm (AI hay mentor)
-- ---------------------------------------------------------------
ALTER TABLE homework_grade_history
    ADD COLUMN IF NOT EXISTS graded_by_type VARCHAR(20);

COMMENT ON COLUMN homework_submissions.graded_by_type IS
    'Ai chấm bài này: ''ai'' (AI tự chấm) | ''mentor'' (mentor chấm tay). NULL = chưa chấm.';
COMMENT ON COLUMN homework_submissions.assigned_grader_id IS
    'Mentor được giao chấm bài này (mentor 1-1 của học viên). NULL = học viên không mua mentor → AI chấm tự động.';
COMMENT ON COLUMN homework_grade_history.graded_by_type IS
    'Ai thực hiện lần chấm/sửa điểm này: ''ai'' | ''mentor''. NULL = chưa chấm.';
