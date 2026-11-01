package com.sba301.englishcenter.service.impl;

import com.sba301.englishcenter.config.AppProperties;
import com.sba301.englishcenter.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Gửi email HTML qua JavaMailSender (Gmail SMTP app password).
 * From address mặc định lấy từ spring.mail.username.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private static final String BRAND = "EnglishHub";

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Override
    public void sendAccountActivationEmail(String toEmail, String fullName, String activationLink) {
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#1e293b;max-width:560px;margin:0 auto;padding:24px">
                  <h2 style="color:#4338ca;margin-bottom:4px">%s</h2>
                  <p style="font-size:15px;line-height:1.6">Xin chào <strong>%s,</strong></p>
                  <p style="font-size:15px;line-height:1.6">
                    Bạn (hoặc ai đó) vừa đăng ký tài khoản <strong>%s</strong> với email này.
                    Để hoàn tất, vui lòng bấm nút bên dưới và <strong>đặt mật khẩu</strong> cho tài khoản của bạn:
                  </p>
                  <p style="text-align:center;margin:28px 0">
                    <a href="%s"
                       style="display:inline-block;background:linear-gradient(90deg,#4f46e5,#4338ca);color:#ffffff;
                              text-decoration:none;font-weight:bold;padding:12px 28px;border-radius:10px">
                      Đặt mật khẩu &amp; kích hoạt tài khoản
                    </a>
                  </p>
                  <p style="font-size:13px;color:#64748b;line-height:1.6">
                    Link hết hạn sau <strong>%d giờ</strong>. Nếu nút không hoạt động, sao chép đường dẫn này vào trình duyệt:<br>
                    <span style="word-break:break-all">%s</span>
                  </p>
                  <p style="font-size:13px;color:#64748b">
                    Nếu bạn không đăng ký tài khoản, hãy bỏ qua email này — tài khoản sẽ không được kích hoạt.
                  </p>
                  <hr style="border:none;border-top:1px solid #e2e8f0;margin:24px 0 12px">
                  <p style="font-size:12px;color:#94a3b8">%s — Hệ thống đào tạo &amp; Khảo thí chuẩn hoá</p>
                </div>
                """.formatted(BRAND, escape(fullName), BRAND, escape(activationLink),
                appProperties.getAuth().getActivation().getTokenExpiryHours(),
                escape(activationLink), BRAND);

        send(toEmail, "Kích hoạt tài khoản " + BRAND + " của bạn", html);
    }

    @Override
    public void sendMentorWelcomeEmail(String toEmail, String fullName, String tempPassword) {
        String loginUrl = appProperties.getFrontend().getBaseUrl() + "/login";
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;color:#1e293b;max-width:560px;margin:0 auto;padding:24px">
                  <h2 style="color:#4338ca;margin-bottom:4px">%s</h2>
                  <p style="font-size:15px;line-height:1.6">Xin chào <strong>%s,</strong></p>
                  <p style="font-size:15px;line-height:1.6">
                    Quản trị viên đã tạo tài khoản <strong>Mentor</strong> cho bạn trên nền tảng %s.
                    Thông tin đăng nhập của bạn:
                  </p>
                  <table style="border-collapse:collapse;margin:16px 0;font-size:14px">
                    <tr>
                      <td style="padding:8px 14px;border:1px solid #e2e8f0;background:#f8fafc;color:#64748b">Email đăng nhập</td>
                      <td style="padding:8px 14px;border:1px solid #e2e8f0;font-weight:bold">%s</td>
                    </tr>
                    <tr>
                      <td style="padding:8px 14px;border:1px solid #e2e8f0;background:#f8fafc;color:#64748b">Mật khẩu tạm thời</td>
                      <td style="padding:8px 14px;border:1px solid #e2e8f0;font-weight:bold;font-family:monospace;font-size:15px">%s</td>
                    </tr>
                  </table>
                  <p style="font-size:14px;line-height:1.6">
                    ⚠️ Vì lý do bảo mật, bạn <strong>bắt buộc phải đổi mật khẩu</strong> ở lần đăng nhập đầu tiên.
                  </p>
                  <p style="text-align:center;margin:24px 0">
                    <a href="%s"
                       style="display:inline-block;background:linear-gradient(90deg,#4f46e5,#4338ca);color:#ffffff;
                              text-decoration:none;font-weight:bold;padding:12px 28px;border-radius:10px">
                      Đăng nhập ngay
                    </a>
                  </p>
                  <p style="font-size:13px;color:#64748b">
                    Không chia sẻ email này cho người khác. Nếu bạn không mong đợi tài khoản này,
                    hãy liên hệ quản trị viên của trung tâm.
                  </p>
                  <hr style="border:none;border-top:1px solid #e2e8f0;margin:24px 0 12px">
                  <p style="font-size:12px;color:#94a3b8">%s — Hệ thống đào tạo &amp; Khảo thí chuẩn hoá</p>
                </div>
                """.formatted(BRAND, escape(fullName), BRAND, escape(toEmail), escape(tempPassword),
                escape(loginUrl), BRAND);

        send(toEmail, "Tài khoản Mentor " + BRAND + " của bạn", html);
    }

    private void send(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email '{}' sent to {}", subject, toEmail);
        } catch (MessagingException e) {
            throw new IllegalStateException("Không thể tạo email gửi " + toEmail, e);
        }
    }

    /** Escape HTML tối giản cho giá trị nhúng vào template. */
    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
