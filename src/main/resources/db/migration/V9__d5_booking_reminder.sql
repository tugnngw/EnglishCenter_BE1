-- =====================================================
-- Migration V9: Dev 5 - Nhắc lịch học 1-1 (TASK-D5-02 "sắp đến giờ học", D5-18)
-- Đánh dấu booking đã được nhắc để job chạy mỗi phút không gửi trùng.
-- CHỈ thêm mới, không sửa migration cũ.
-- =====================================================

ALTER TABLE bookings ADD COLUMN reminder_sent_at TIMESTAMP;

-- Job tìm booking confirmed chưa nhắc, sắp bắt đầu
CREATE INDEX IF NOT EXISTS idx_bookings_reminder ON bookings(status, reminder_sent_at);
