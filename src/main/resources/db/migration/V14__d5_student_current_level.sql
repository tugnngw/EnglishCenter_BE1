-- Trình độ hiện tại của người học, cập nhật sau mỗi lần AI chấm bài Test trình độ / Luyện tập AI
ALTER TABLE student_profiles ADD COLUMN IF NOT EXISTS current_level VARCHAR(20);
ALTER TABLE student_profiles ADD COLUMN IF NOT EXISTS current_score NUMERIC(6,2);
