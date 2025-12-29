package nan.produced.prism.core.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.security.api.CloudAuthUser;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 解析 Gateway 传递的 CLOUD_AUTH 头
 * <p>
 * 格式: Base64(JSON)
 * JSON 内容: {publicId, userUuid, roles, tier}
 * <p>
 * 示例:
 * <pre>
 * 原始 JSON:
 * {
 *   "publicId": "u_2Xk9P7qL",
 *   "userUuid": "123e4567-e89b-12d3-a456-426614174000",
 *   "roles": ["ROLE_END_USER"],
 *   "tier": "FREE"
 * }
 *
 * Base64 编码后:
 * eyJwdWJsaWNJZCI6InVfMlhrOVA3cUwiLCJ1c2VyVXVpZCI6IjEyM2U0NTY3LWU4OWItMTJkMy1hNDU2LTQyNjYxNDE3NDAwMCIsInJvbGVzIjpbIlJPTEVfVVNFUiJdLCJ0aWVyIjoiRlJFRSJ9
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudAuthHeaderParser {

    public static final String HEADER_NAME = "CLOUD_AUTH";

    private final ObjectMapper objectMapper;

    /**
     * 解析 CLOUD_AUTH 头
     *
     * @param headerValue Base64 编码的 JSON 字符串
     * @return CloudAuthUser 对象
     * @throws InvalidCloudAuthException 如果头格式无效
     */
    public CloudAuthUser parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new InvalidCloudAuthException(ErrorCode.UNAUTHORIZED, "CLOUD_AUTH header is missing or empty");
        }

        try {
            // 1. Base64 解码
            byte[] decodedBytes = Base64.getUrlDecoder().decode(headerValue);
            String jsonString = new String(decodedBytes, StandardCharsets.UTF_8);

            log.debug("Decoded CLOUD_AUTH JSON: {}", jsonString);

            // 2. 解析 JSON
            JsonNode jsonNode = objectMapper.readTree(jsonString);

            // 3. 提取字段
            String publicId = getRequiredString(jsonNode, "publicId");
            String userUuid = getString(jsonNode, "userUuid");
            List<String> roles = getRoles(jsonNode);
            String tier = getString(jsonNode, "tier");

            return new CloudAuthUser(publicId, userUuid, roles, tier);

        } catch (InvalidCloudAuthException e) {
            throw e;  // 直接抛出已知异常
        } catch (Exception e) {
            log.error("Failed to parse CLOUD_AUTH header: {}", headerValue, e);
            throw new InvalidCloudAuthException(ErrorCode.INVALID_CLOUD_AUTH_HEADER, "Invalid CLOUD_AUTH header format", e);
        }
    }

    /**
     * 获取必需的字符串字段
     */
    private String getRequiredString(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            throw new InvalidCloudAuthException(ErrorCode.INVALID_CLOUD_AUTH_HEADER, "Required field missing: " + fieldName);
        }
        return fieldNode.asText();
    }

    /**
     * 获取可选的字符串字段
     */
    private String getString(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        return (fieldNode == null || fieldNode.isNull()) ? null : fieldNode.asText();
    }

    /**
     * 获取角色列表
     */
    private List<String> getRoles(JsonNode node) {
        JsonNode rolesNode = node.get("roles");
        if (rolesNode == null || !rolesNode.isArray()) {
            return List.of();
        }

        return objectMapper.convertValue(rolesNode,
            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }
}
