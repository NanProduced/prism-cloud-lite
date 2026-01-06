package nan.produced.prism.core.notification.email;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminMailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;
    private final AdminMailLogRepository mailLogRepository;
    private final MailProps mailProps;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Transactional
    public void sendTemplateEmail(AdminMailTemplate template,
                                  String to,
                                  String subject,
                                  String thymeleafTemplatePath,
                                  Map<String, Object> variables) {
        if (!StringUtils.hasText(to)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "to email is required");
        }
        if (!StringUtils.hasText(subject)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "subject is required");
        }
        if (!StringUtils.hasText(thymeleafTemplatePath)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "template path is required");
        }

        String toEmail = to.trim().toLowerCase();
        String from = resolveFromAddress();

        Map<String, Object> safeVars = new HashMap<>();
        if (variables != null) {
            safeVars.putAll(variables);
        }
        safeVars.putIfAbsent("productName", mailProps.getProductName());
        String prismUrl = resolvePrismUrl();
        safeVars.putIfAbsent("prismUrl", prismUrl);
        safeVars.putIfAbsent("consoleUrl", prismUrl);

        AdminMailLogEntity logEntity = AdminMailLogEntity.builder()
                .id(UUID.randomUUID())
                .template(template.name())
                .toEmail(toEmail)
                .subject(subject.length() > 200 ? subject.substring(0, 200) : subject)
                .variables(safeVars)
                .success(false)
                .errorMessage(null)
                .actorPublicId(safeActor())
                .build();

        try {
            String html = renderHtml(thymeleafTemplatePath, safeVars);
            sendHtml(toEmail, from, mailProps.getFromName(), subject, html);
            logEntity.setSuccess(true);
            mailLogRepository.save(logEntity);
        } catch (Exception ex) {
            log.warn("Admin mail send failed: template={}, to={}", template, toEmail, ex);
            logEntity.setSuccess(false);
            logEntity.setErrorMessage(trim(ex.getMessage(), 2000));
            mailLogRepository.save(logEntity);
            throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "邮件发送失败: " + ex.getMessage());
        }
    }

    private String renderHtml(String templatePath, Map<String, Object> variables) {
        Context context = new Context();
        if (variables != null) {
            variables.forEach(context::setVariable);
        }
        return templateEngine.process(templatePath, context);
    }

    private void sendHtml(String to, String from, String fromName, String subject, String html) throws Exception {
        var message = mailSender.createMimeMessage();
        var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
        helper.setTo(to);
        if (StringUtils.hasText(fromName)) {
            helper.setFrom(from, fromName);
        } else {
            helper.setFrom(from);
        }
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(mailProps.getFrom())) {
            return mailProps.getFrom().trim();
        }
        if (StringUtils.hasText(mailUsername)) {
            return mailUsername.trim();
        }
        throw new BizException(ErrorCode.EXTERNAL_SERVICE_ERROR, "未配置发件邮箱（prism.mail.from 或 spring.mail.username）");
    }

    private String resolvePrismUrl() {
        if (StringUtils.hasText(mailProps.getPrismUrl())) {
            return mailProps.getPrismUrl().trim();
        }
        else return "https://prism.nanproduced.cloud/";
    }

    private static String safeActor() {
        try {
            return CloudAuthContext.hasAuthenticatedUser() ? CloudAuthContext.getCurrentPublicId() : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String trim(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
