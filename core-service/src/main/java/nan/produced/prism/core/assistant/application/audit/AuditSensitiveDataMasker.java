package nan.produced.prism.core.assistant.application.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AuditSensitiveDataMasker {

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+?86)?[\\s-]?(?:1[3-9]\\d{9}|0\\d{2,3}[\\s-]?\\d{7,8})"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    );

    private static final String[] SENSITIVE_KEYS = {
            "phone", "mobile", "cell", "tel", "telephone",
            "email", "mail", "e-mail",
            "idCard", "idcard", "id_card", "identity", "idNo", "id_no",
            "password", "pwd", "passwd", "secret", "token", "apiKey", "api_key",
            "creditCard", "credit_card", "bankCard", "bank_card"
    };

    private static final String MASK_PHONE = "****-****-****";
    private static final String MASK_EMAIL = "****@****.***";
    private static final String MASK_SENSITIVE = "********";

    public JsonNode mask(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }

        try {
            return maskNode(node);
        } catch (Exception e) {
            log.warn("Failed to mask sensitive data, returning original", e);
            return node;
        }
    }

    private JsonNode maskNode(JsonNode node) {
        if (node.isObject()) {
            return maskObject((ObjectNode) node);
        } else if (node.isArray()) {
            return maskArray((ArrayNode) node);
        } else if (node.isTextual()) {
            return maskTextValue(node.asText());
        }
        return node;
    }

    private JsonNode maskObject(ObjectNode object) {
        ObjectNode result = object.deepCopy();
        Iterator<Map.Entry<String, JsonNode>> fields = result.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (isSensitiveKey(key)) {
                if (value.isTextual()) {
                    result.set(key, maskTextValueByKey(key, value.asText()));
                } else {
                    result.put(key, MASK_SENSITIVE);
                }
            } else if (value.isContainerNode()) {
                result.set(key, maskNode(value));
            } else if (value.isTextual()) {
                result.set(key, maskTextValue(value.asText()));
            }
        }
        return result;
    }

    private JsonNode maskArray(ArrayNode array) {
        ArrayNode result = array.deepCopy();
        for (int i = 0; i < result.size(); i++) {
            JsonNode element = result.get(i);
            if (element.isContainerNode()) {
                result.set(i, maskNode(element));
            } else if (element.isTextual()) {
                result.set(i, maskTextValue(element.asText()));
            }
        }
        return result;
    }

    private JsonNode maskTextValue(String text) {
        if (text == null || text.isEmpty()) {
            return new TextNode(text);
        }

        String masked = text;

        if (PHONE_PATTERN.matcher(masked).find()) {
            masked = PHONE_PATTERN.matcher(masked).replaceAll(MASK_PHONE);
        }

        if (EMAIL_PATTERN.matcher(masked).find()) {
            masked = EMAIL_PATTERN.matcher(masked).replaceAll(MASK_EMAIL);
        }

        return new TextNode(masked);
    }

    private JsonNode maskTextValueByKey(String key, String text) {
        String lowerKey = key.toLowerCase();

        if (lowerKey.contains("phone") || lowerKey.contains("mobile") || lowerKey.contains("cell") ||
                lowerKey.contains("tel") || lowerKey.contains("telephone")) {
            return new TextNode(maskPhone(text));
        }

        if (lowerKey.contains("email") || lowerKey.contains("mail")) {
            return new TextNode(maskEmail(text));
        }

        return new TextNode(MASK_SENSITIVE);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return MASK_PHONE;
        }

        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.length() == 11) {
            return digits.substring(0, 3) + "****" + digits.substring(7);
        } else if (digits.length() >= 7) {
            return digits.substring(0, Math.min(3, digits.length())) + "****" +
                    digits.substring(Math.max(digits.length() - 4, 0));
        }

        return MASK_PHONE;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return MASK_EMAIL;
        }

        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];

        String maskedLocal;
        if (local.length() <= 2) {
            maskedLocal = "**";
        } else {
            maskedLocal = local.charAt(0) + "****" + local.charAt(local.length() - 1);
        }

        int dotIndex = domain.lastIndexOf('.');
        String maskedDomain;
        if (dotIndex > 0) {
            String domainName = domain.substring(0, dotIndex);
            String tld = domain.substring(dotIndex);
            if (domainName.length() <= 2) {
                maskedDomain = "**" + tld;
            } else {
                maskedDomain = domainName.charAt(0) + "****" + tld;
            }
        } else {
            maskedDomain = "****.***";
        }

        return maskedLocal + "@" + maskedDomain;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        for (String sensitiveKey : SENSITIVE_KEYS) {
            if (lowerKey.contains(sensitiveKey.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
