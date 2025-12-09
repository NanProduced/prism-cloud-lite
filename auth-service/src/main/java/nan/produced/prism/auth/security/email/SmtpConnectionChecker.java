package nan.produced.prism.auth.security.email;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 启动时检测 SMTP 可用性，方便尽早发现邮箱配置问题。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpConnectionChecker implements ApplicationRunner {

    private final JavaMailSender mailSender;

    @Override
    public void run(ApplicationArguments args) {
        if (!(mailSender instanceof JavaMailSenderImpl sender)) {
            log.info("跳过SMTP连接检查，mailSender类型为 {}", mailSender.getClass().getName());
            return;
        }

        if (!StringUtils.hasText(sender.getHost())) {
            log.warn("跳过SMTP连接检查：未配置spring.mail.host");
            return;
        }

        try {
            sender.testConnection();
            log.info(
                "SMTP连接检查成功，服务器 {}:{}, 用户名 {}",
                sender.getHost(),
                sender.getPort(),
                StringUtils.hasText(sender.getUsername()) ? sender.getUsername() : "<anonymous>"
            );
        }
        catch (MessagingException ex) {
            log.error(
                "SMTP连接检查失败，服务器 {}:{}, 用户名 {}，原因 {}",
                sender.getHost(),
                sender.getPort(),
                sender.getUsername(),
                ex.getMessage(),
                ex
            );
        }
    }
}
