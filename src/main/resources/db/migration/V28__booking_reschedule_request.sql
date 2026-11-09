-- =====================================================
-- Migration V28: Dev 5 - Học viên xin dời lịch 1 buổi kèm 1-1
-- Học viên chỉ GỬI YÊU CẦU (lý do + giờ mong muốn), mentor quyết định: dời (yêu cầu tự xóa) hoặc từ chối.
-- Mỗi buổi tối đa 1 yêu cầu đang chờ (gửi lại thì ghi đè). Giờ lưu UTC (TASK D5-19).
-- CHỈ thêm mới, không sửa migration cũ.
-- =====================================================

ALTER TABLE bookings
    ADD COLUMN IF NOT EXISTS reschedule_requested_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reschedule_preferred_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reschedule_request_note   VARCHAR(500);
