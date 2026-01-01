package nan.produced.prism.core.assistant.infrastructure.crypto;

import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmCryptoService {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "aesgcm:";

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmCryptoService(String base64Key) {
        if (!StringUtils.hasText(base64Key)) {
            this.secretKey = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != KEY_BYTES) {
            throw new IllegalArgumentException("Invalid master key length: expected=32 bytes base64-decoded");
        }
        this.secretKey = new SecretKeySpec(raw, "AES");
    }

    public String encryptToString(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        if (secretKey == null) {
            throw new IllegalStateException("BYOK master key not configured (set PRISM_AI_CREDENTIALS_MASTER_KEY)");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Encrypt failed", e);
        }
    }

    public String decryptFromString(String envelope) {
        if (envelope == null) {
            return null;
        }
        if (secretKey == null) {
            throw new IllegalStateException("BYOK master key not configured (set PRISM_AI_CREDENTIALS_MASTER_KEY)");
        }
        if (!envelope.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported ciphertext format");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(envelope.substring(PREFIX.length()));
            if (combined.length <= IV_BYTES) {
                throw new IllegalArgumentException("Ciphertext too short");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decrypt failed", e);
        }
    }
}
