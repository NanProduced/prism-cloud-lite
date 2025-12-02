package nan.produced.prism.auth.security.signature;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.exception.ErrorCode;

/**
 * 服务签名工具类
 * 用于生成和验证服务间 RPC 调用的 HMAC-SHA256 签名
 *
 * 签名内容：method + path + body + timestamp
 * 签名算法：Base64(HMAC-SHA256(content, secret))
 */
@Slf4j
public class ServiceSignatureUtil {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * 计算服务签名
     *
     * @param method HTTP 方法（GET、POST 等）
     * @param path 请求路径
     * @param body 请求 body 内容（JSON 字符串）
     * @param timestamp 时间戳（毫秒）
     * @param secret 共享密钥
     * @return Base64 编码的签名
     */
    public static String calculateSignature(String method, String path, String body, long timestamp, String secret) {
        try {
            // 构建待签名内容
            String message = buildMessage(method, path, body, timestamp);

            // 计算 HMAC-SHA256
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                0,
                secret.getBytes(StandardCharsets.UTF_8).length,
                ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));

            // Base64 编码
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            log.error("计算签名失败: {}", e.getMessage(), e);
            throw new BizException(ErrorCode.CALCULATE_SIGNATURE_FAILED, e);
        }
    }

    /**
     * 验证签名
     *
     * @param signature 要验证的签名（Base64 编码）
     * @param method HTTP 方法
     * @param path 请求路径
     * @param body 请求 body
     * @param timestamp 时间戳
     * @param secret 共享密钥
     * @return 签名是否有效
     */
    public static boolean verifySignature(String signature, String method, String path, String body, long timestamp, String secret) {
        try {
            String expectedSignature = calculateSignature(method, path, body, timestamp, secret);
            return constantTimeEquals(signature, expectedSignature);
        } catch (Exception e) {
            log.warn("签名验证异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 常时间比较，防止时序攻击
     *
     * @param a 第一个字符串
     * @param b 第二个字符串
     * @return 是否相等
     */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    /**
     * 构建待签名消息
     *
     * @param method HTTP 方法
     * @param path 请求路径
     * @param body 请求 body
     * @param timestamp 时间戳
     * @return 待签名内容
     */
    private static String buildMessage(String method, String path, String body, long timestamp) {
        // 标准化方法为大写
        String normalizedMethod = method != null ? method.toUpperCase() : "POST";
        // 处理 null 或空的 body
        String normalizedBody = body != null ? body : "";

        // 按照规范顺序构建消息
        return normalizedMethod + path + normalizedBody + timestamp;
    }
}
