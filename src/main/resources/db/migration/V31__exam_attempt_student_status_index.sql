-- =====================================================
-- Migration V31: Index cho luồng "bài thi chờ nâng cấp gói Mentor" (V30)
-- -----------------------------------------------------------
-- V30 đã thêm trạng thái 'awaiting_mentor' cho exam_attempts.
-- Luồng V30 quét lại các lượt thi đang lưu nháp theo (student_id, status) mỗi khi
-- học viên thanh toán gói mentor 1-1 → cần index để truy vấn này không full-scan
-- bảng exam_attempts (bảng lớn nhất của module Exam).
--
-- CHỈ tạo index, không sửa/xóa dữ liệu hay migration cũ.
-- =====================================================

-- Truy vấn: exam_attempts WHERE student_id = ? AND status = 'awaiting_mentor' ORDER BY end_time
CREATE INDEX IF NOT EXISTS idx_exam_attempts_student_status
    ON exam_attempts (student_id, status);

COMMENT ON INDEX idx_exam_attempts_student_status IS
    'V30: hỗ trợ quét lại các lượt thi lưu nháp (awaiting_mentor) khi học viên nâng cấp gói Mentor 1-1';
