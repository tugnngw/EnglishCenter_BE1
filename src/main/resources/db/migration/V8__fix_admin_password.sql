-- =====================================================
-- Migration V8: Sửa mật khẩu tài khoản admin seed ở V1
-- V1 ghi chú mật khẩu là Admin@123 nhưng hash BCrypt lưu trong DB không khớp -> admin không đăng nhập được.
-- Đặt lại đúng hash BCrypt (cost 10) của "Admin@123", mở khóa và xóa số lần đăng nhập sai.
-- Chỉ áp dụng cho admin@gmail.com. ĐỔI PASSWORD NGAY SAU KHI DEPLOY.
-- =====================================================

UPDATE users
SET password              = '$2a$10$b04iIUOtsa1sunmnwzmnkev6ee78IZB33K.dh.vK.ZvMDHkclJ5om',
    failed_login_attempts = 0,
    locked_until          = NULL,
    last_updated          = CURRENT_TIMESTAMP
WHERE email = 'admin@gmail.com';
