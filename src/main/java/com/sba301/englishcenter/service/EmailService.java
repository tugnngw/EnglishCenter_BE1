package com.sba301.englishcenter.service;

/**
 * Gửi email nghiệp vụ (SMTP Gmail — cấu hình qua spring.mail.* + .env).
 * Mọi hàm ném MailException khi gửi lỗi; caller tự bọc try/catch
 * để không rollback luồng nghiệp vụ (đăng ký / tạo mentor).
 */
public interface EmailService {

    /**
     * Email kích hoạt tài khoản cho user tự đăng ký:
     * chứa link đặt mật khẩu (trang /set-password trên web).
     */
    void sendAccountActivationEmail(String toEmail, String fullName, String activationLink);

    /**
     * Email chào mừng mentor do admin tạo:
     * chứa mật khẩu tạm + hướng dẫn đổi mật khẩu ở lần đăng nhập đầu.
     */
    void sendMentorWelcomeEmail(String toEmail, String fullName, String tempPassword);
}
