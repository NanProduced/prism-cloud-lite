package nan.produced.prism.device.boot.security.integration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.boot.security.DeviceSecurityProps;
import nan.produced.prism.device.common.exception.business.BusinessException;
import nan.produced.prism.device.common.utils.SignatureUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

import static nan.produced.prism.device.common.exception.business.BusinessErrorCode.SYSTEM_ERROR;

/**
 * 服务签名请求拦截器
 * 为所有向 Core-Service 的 Feign 请求添加服务签名
 * <p>
 * 添加的请求头：
 * - X-Service-From: 调用服务标识
 * - X-Timestamp: 请求时间戳
 * - X-Signature: HMAC-SHA256 签名
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceSignatureInterceptor implements RequestInterceptor {

    @Value("${spring.application.name}")
    private String serviceId;

    private final DeviceSecurityProps securityProps;

    @Override
    public void apply(RequestTemplate template) {
        try {
            String signatureSecret = securityProps.getServiceSignature().getSecret();
            if (signatureSecret == null || signatureSecret.isBlank()) {
                log.warn("服务签名密钥未配置，跳过签名");
                return;
            }

            long timestamp = System.currentTimeMillis();

            // 提取请求信息
            String method = template.method();
            String path = template.path();
            String body = extractBody(template);

            // 计算签名
            String signature = SignatureUtils.calculateSignature(
                    method, path, body, timestamp, signatureSecret
            );

            // 添加请求头
            template.header("X-Service-From", serviceId);
            template.header("X-Timestamp", String.valueOf(timestamp));
            template.header("X-Signature", signature);

            log.debug("为请求添加服务签名: path={}, signature={}", path, signature.substring(0, Math.min(10, signature.length())) + "...");
        } catch (Exception e) {
            log.error("为请求添加签名失败: {}", e.getMessage(), e);
            throw new BusinessException(SYSTEM_ERROR, "为请求添加签名失败: " + e.getMessage(), e);
        }
    }

    private String extractBody(RequestTemplate template) {
        if (template.body() == null) {
            return "";
        }
        return new String(template.body(), StandardCharsets.UTF_8);
    }
}
