-- =====================================================
-- Migration V30: Bài thi cuối kỳ - trạng thái "chờ nâng cấp gói mentor"
-- -----------------------------------------------------------
-- Flow nghiệp vụ mới (V30):
--   1. Học viên KHÔNG mua gói mentor vẫn làm + nộp ĐƯỢC toàn bộ đề cuối kỳ.
--      Phần trắc nghiệm được chấm tự động ngay; phần tự luận (Writing/Speaking)
--      được LƯU NHÁP ở trạng thái 'awaiting_mentor' (chưa có mentor chấm).
--   2. Học viên ĐÃ mua gói mentor → bài tự luận vào hàng đợi chấm của mentor
--      (trạng thái 'submitted') ngay khi nộp.
--   3. Khi học viên mua gói mentor SAU đó → mọi lượt thi đang 'awaiting_mentor'
--      được chuyển sang 'submitted' (mentor thấy và chấm), học viên KHÔNG phải
--      làm lại bài.
--
-- CHỈ thêm mới giá trị enum, không sửa/xóa dữ liệu hay migration cũ.
-- =====================================================

-- Giá trị mới chưa dùng được trong cùng transaction với DML → ADD VALUE rồi COMMIT ngầm.
ALTER TYPE attempt_status_enum ADD VALUE IF NOT EXISTS 'awaiting_mentor';

COMMENT ON TYPE attempt_status_enum IS
    'Trạng thái lượt thi: in_progress (đang làm), submitted (đã nộp, chờ mentor chấm), '
    'awaiting_mentor (đã nộp, phần tự luận lưu nháp chờ nâng cấp gói mentor), '
    'graded (đã chấm xong), expired (hết giờ, hệ thống tự nộp)';
