-- =====================================================
-- V16: Đề luyện tập AI nhiều kỹ năng dùng chung module Exam
--   1. exam_scope = 'practice': đề luyện tập (Listening / Reading / Writing / Speaking),
--      gắn chứng chỉ không bắt buộc, không thuộc khóa học
--   2. exam_attempt_details.ai_evaluation: kết quả AI chấm câu Writing / Speaking
-- =====================================================

ALTER TYPE exam_scope_enum ADD VALUE IF NOT EXISTS 'practice';

-- So sánh dạng text: giá trị enum vừa thêm chưa dùng được trong cùng transaction
ALTER TABLE exams DROP CONSTRAINT IF EXISTS chk_exam_scope;
ALTER TABLE exams ADD CONSTRAINT chk_exam_scope CHECK (
    (exam_scope::text = 'trial' AND certificate_id IS NOT NULL) OR
    (exam_scope::text = 'course' AND course_id IS NOT NULL) OR
    (exam_scope::text = 'practice' AND course_id IS NULL)
);

-- {skill, bandScore, cefrLevel, certificateName, minScore, maxScore, 4 tiêu chí, feedback, suggestions, model, evaluatedAt}
ALTER TABLE exam_attempt_details
    ADD COLUMN IF NOT EXISTS ai_evaluation JSONB;
