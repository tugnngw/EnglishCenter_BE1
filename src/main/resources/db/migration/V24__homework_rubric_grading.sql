-- =====================================================
-- Migration V24: Chấm bài tay theo rubric + gợi ý AI cho mentor
--
-- Mục tiêu (chỉ CÔNG THÊM — không xoá/đổi kiểu cột cũ):
--   1. `homework_submissions.rubric_scores` — điểm + nhận xét 1-1 của từng tiêu chí
--      rubric mà mentor chấm (JSONB array).
--   2. `homework_grade_history.rubric_scores` — lưu lại rubric điểm ở từng lần chấm/sửa điểm.
--
-- Tương thích ngược: mọi cột mới đều NULLABLE, dữ liệu cũ chạy y hệt.
-- =====================================================

ALTER TABLE homework_submissions
    ADD COLUMN IF NOT EXISTS rubric_scores JSONB;

ALTER TABLE homework_grade_history
    ADD COLUMN IF NOT EXISTS rubric_scores JSONB;

COMMENT ON COLUMN homework_submissions.rubric_scores IS
    'Điểm + nhận xét 1-1 theo từng tiêu chí rubric mà mentor chấm: [{"criterion":"...","score":6.5,"comment":"..."}]';
COMMENT ON COLUMN homework_grade_history.rubric_scores IS
    'Snapshot rubric điểm tại thời điểm lần chấm này.';
