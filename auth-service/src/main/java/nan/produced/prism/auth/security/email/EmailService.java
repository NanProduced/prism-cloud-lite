package nan.produced.prism.auth.security.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.ErrorCode;
import nan.produced.prism.auth.common.exception.InfraException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * 邮件服务
 * 负责发送各类邮件（OTP、通知等）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.properties.mail.smtp.from:}")
    private String fromEmail;

    /**
     * 发送OTP邮件
     * @param email 收件人邮箱
     * @param otp OTP验证码
     * @param otpValidityMinutes OTP有效期（分钟）
     */
    public void sendOtpEmail(String email, String otp, long otpValidityMinutes) {
        try {
            String subject = "Prism Cloud - 邮箱验证码";
            String htmlContent = buildOtpEmailHtml(otp, otpValidityMinutes);
            sendHtmlEmail(email, subject, htmlContent);
            log.info("OTP email sent successfully to: {}", email);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send OTP email to: {}", email, e);
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "邮件发送失败: " + e.getMessage(), e);
        }
    }

    /**
     * 发送HTML格式的邮件
     * @param email 收件人邮箱
     * @param subject 邮件主题
     * @param htmlContent HTML内容
     */
    public void sendHtmlEmail(String email, String subject, String htmlContent) throws MessagingException, UnsupportedEncodingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail, "Prism Cloud");
        helper.setTo(email);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        mailSender.send(message);
    }

    /**
     * 构建OTP邮件HTML内容
     * @param otp OTP验证码
     * @param otpValidityMinutes OTP有效期（分钟）
     * @return HTML内容
     */
    private String buildOtpEmailHtml(String otp, long otpValidityMinutes) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { text-align: center; color: #333; margin-bottom: 30px; }
                    .content { background-color: #f5f5f5; padding: 20px; border-radius: 8px; }
                    .otp-code {
                        text-align: center;
                        font-size: 32px;
                        font-weight: bold;
                        letter-spacing: 8px;
                        color: #4A90E2;
                        margin: 30px 0;
                        font-family: 'Courier New', monospace;
                    }
                    .info { color: #666; font-size: 14px; margin-top: 20px; }
                    .warning { color: #e74c3c; font-size: 12px; margin-top: 15px; }
                    .footer { text-align: center; color: #999; font-size: 12px; margin-top: 40px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Prism Cloud</h1>
                    </div>
                    <div class="content">
                        <p>亲爱的用户，</p>
                        <p>感谢您使用 Prism Cloud。您的邮箱验证码为：</p>
                        <div class="otp-code">%s</div>
                        <div class="info">
                            <p>✓ 此验证码有效期为 %d 分钟</p>
                            <p>✓ 请勿与他人分享此验证码</p>
                            <p>✓ 请在浏览器中输入此验证码来完成验证</p>
                        </div>
                        <div class="warning">
                            <strong>⚠️ 安全提示：</strong>官方邮件不会要求您提供密码或个人隐私信息，请谨防诈骗。
                        </div>
                    </div>
                    <div class="footer">
                        <p>© 2024 Prism Cloud. All rights reserved.</p>
                    </div>
                </div>
            </body>
            </html>
            """.formatted(otp, otpValidityMinutes);
    }

}
