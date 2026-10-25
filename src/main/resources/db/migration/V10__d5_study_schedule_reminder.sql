-- =====================================================
-- Migration V10: Dev 5 - Lịch học cá nhân (study_schedules, có từ V1)
-- Đánh dấu đã gửi nhắc cho lần học hiện tại để job chạy mỗi phút không gửi trùng.
-- scheduled_time / reminder_sent_at lưu giờ UTC (TASK D5-19).
-- CHỈ thêm mới, không sửa migration cũ.
-- =====================================================

ALTER TABLE study_schedules ADD COLUMN reminder_sent_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_study_schedules_student_time ON study_schedules(student_id, scheduled_time);
CREATE INDEX IF NOT EXISTS idx_study_schedules_reminder ON study_schedules(status, reminder_sent_at, scheduled_time);
