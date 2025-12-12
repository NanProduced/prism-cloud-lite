package nan.produced.prism.device.common.utils;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 内部接口调用签名工具
 *
 * @author Nan
 */
@Slf4j
public class SignatureUtils {

    private static final String ALGORITHM = "HmacSHA256";

    private SignatureUtils() {
    }

    public static String calculateSignature(String method, String path, String body, long timestamp, String secret) {
        try {
            String message = buildMessage(method, path, body, timestamp);
            Mac mac = Mac.getInstance(ALGORITHM);
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, 0, keyBytes.length, ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate service signature", ex);
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

    private static String buildMessage(String method, String path, String body, long timestamp) {
        String normalizedMethod = method != null ? method.toUpperCase() : "POST";
        String normalizedBody = body != null ? body : "";
        return normalizedMethod + path + normalizedBody + timestamp;
    }
}
