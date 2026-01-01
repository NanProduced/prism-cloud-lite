package nan.produced.prism.core.assistant.application.ingest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Hashing {

    private Hashing() {
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            return toHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

