-- Dev 3: mentor nhận chấm một lượt thi (tránh 2 mentor chấm đè cùng một bài)
ALTER TABLE exam_attempts
    ADD COLUMN IF NOT EXISTS grading_by UUID REFERENCES users (user_id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS grading_claimed_at TIMESTAMP;
