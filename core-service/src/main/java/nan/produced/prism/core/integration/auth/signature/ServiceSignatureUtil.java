package nan.produced.prism.core.integration.auth.signature;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ServiceSignatureUtil {

    private static final String ALGORITHM = "HmacSHA256";

    private ServiceSignatureUtil() {
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

    private static String buildMessage(String method, String path, String body, long timestamp) {
        String normalizedMethod = method != null ? method.toUpperCase() : "POST";
        String normalizedBody = body != null ? body : "";
        return normalizedMethod + path + normalizedBody + timestamp;
    }
}
