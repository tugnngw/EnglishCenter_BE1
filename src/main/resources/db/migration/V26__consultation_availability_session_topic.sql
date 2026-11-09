-- =====================================================
-- Migration V26: Dev 5 - Lịch rảnh khi xin tư vấn + nội dung từng buổi kèm 1-1
-- - consultation_requests.available_slots: khung giờ rảnh hằng tuần học viên gửi kèm SĐT
--   (JSONB, VD [{"day":"MONDAY","from":"19:00","to":"21:00"}]). Giờ lưu theo GIỜ VIỆT NAM
--   vì là khung lặp lại mỗi tuần, không phải 1 thời điểm cụ thể (ngoại lệ của quy tắc UTC D5-19).
-- - mentor_schedules.topic: buổi này học gì (mentor ghi khi tạo gói, học viên xem trước khi thanh toán).
-- CHỈ thêm mới, không sửa migration cũ.
-- =====================================================

ALTER TABLE consultation_requests ADD COLUMN IF NOT EXISTS available_slots JSONB;

ALTER TABLE mentor_schedules ADD COLUMN IF NOT EXISTS topic VARCHAR(255);
