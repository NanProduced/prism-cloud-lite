package nan.produced.prism.auth.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.domain.audit.SecurityEventEntity;
import nan.produced.prism.auth.domain.audit.SecurityEventType;
import nan.produced.prism.auth.domain.audit.repository.SecurityEventRepository;
import nan.produced.prism.auth.domain.session.RememberMeTokenEntity;
import nan.produced.prism.auth.domain.session.repository.RememberMeTokenRepository;
import nan.produced.prism.auth.security.login.LoginAuthType;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import nan.produced.prism.auth.utils.JsonUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    /**
     * Client context headers (propagated from gateway/core-service when invoking internal APIs).
     */
    public static final String HEADER_CLIENT_IP = "X-Client-Ip";
    public static final String HEADER_CLIENT_USER_AGENT = "X-Client-User-Agent";
    public static final String HEADER_CLIENT_DEVICE = "X-Client-Device";

    private final SecurityEventRepository securityEventRepository;
    private final RememberMeTokenRepository rememberMeTokenRepository;

    public void recordLoginSuccess(HttpServletRequest request,
                                   PrismUserPrincipal principal,
                                   LoginAuthType authType,
                                   boolean rememberMe) {
        if (principal == null || principal.getId() == null) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (authType != null) {
            metadata.put("authType", authType.name());
        }
        metadata.put("rememberMe", rememberMe);
        saveEvent(principal.getId(), SecurityEventType.LOGIN_SUCCESS, true, request, null, metadata);
    }

    public void recordPasswordChanged(UUID userId, HttpServletRequest request) {
        if (userId == null) {
            return;
        }
        saveEvent(userId, SecurityEventType.PASSWORD_CHANGED, true, request, null, Map.of());
    }

    public void recordSessionRevoked(UUID userId, String series, HttpServletRequest request) {
        if (userId == null || !StringUtils.hasText(series)) {
            return;
        }
        Optional<RememberMeTokenEntity> tokenOpt = rememberMeTokenRepository.findBySeries(series)
            .filter(token -> userId.equals(token.getUserId()));
        if (tokenOpt.isEmpty()) {
            return;
        }
        RememberMeTokenEntity token = tokenOpt.get();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("series", series);
        saveEvent(userId, SecurityEventType.SESSION_REVOKED, true, request, token, metadata);
    }

    public void recordSessionsRevoked(UUID userId, HttpServletRequest request) {
        if (userId == null) {
            return;
        }
        saveEvent(userId, SecurityEventType.SESSIONS_REVOKED, true, request, null, Map.of());
    }

    public Page<SecurityEventEntity> listUserEvents(UUID userId, int page, int size) {
        if (userId == null) {
            return Page.empty();
        }
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return securityEventRepository.findByUserId(userId, pageable);
    }

    private void saveEvent(UUID userId,
                           SecurityEventType type,
                           boolean success,
                           HttpServletRequest request,
                           RememberMeTokenEntity rememberMeToken,
                           Map<String, Object> metadata) {
        SecurityEventEntity entity = new SecurityEventEntity();
        entity.setUserId(userId);
        entity.setEventType(type);
        entity.setSuccess(success);

        String ip = resolveClientIp(request);
        String userAgent = resolveUserAgent(request);
        String deviceName = resolveDeviceName(request, userAgent);

        if (rememberMeToken != null) {
            // Prefer persisted device context for session revocation.
            if (StringUtils.hasText(rememberMeToken.getIpAddress())) {
                ip = rememberMeToken.getIpAddress();
            }
            if (StringUtils.hasText(rememberMeToken.getUserAgent())) {
                userAgent = rememberMeToken.getUserAgent();
            }
            if (StringUtils.hasText(rememberMeToken.getDeviceName())) {
                deviceName = rememberMeToken.getDeviceName();
            }
        }

        entity.setIpAddress(truncate(ip, 64));
        entity.setUserAgent(truncate(userAgent, 512));
        entity.setDeviceName(truncate(deviceName, 128));
        entity.setMetadata(StringUtils.hasText(metadataJson(metadata)) ? metadataJson(metadata) : "{}");
        securityEventRepository.save(entity);
    }

    private String metadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        return JsonUtils.toJson(metadata);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String clientIp = request.getHeader(HEADER_CLIENT_IP);
        if (StringUtils.hasText(clientIp)) {
            return firstIp(clientIp);
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return firstIp(forwarded);
        }
        return request.getRemoteAddr();
    }

    private String resolveUserAgent(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ua = request.getHeader(HEADER_CLIENT_USER_AGENT);
        if (StringUtils.hasText(ua)) {
            return ua;
        }
        return request.getHeader("User-Agent");
    }

    private String resolveDeviceName(HttpServletRequest request, String userAgent) {
        if (request == null) {
            return truncate(userAgent, 128);
        }
        String custom = request.getHeader(HEADER_CLIENT_DEVICE);
        if (StringUtils.hasText(custom)) {
            return custom;
        }
        custom = request.getHeader("X-Client-Device");
        if (StringUtils.hasText(custom)) {
            return custom;
        }
        return truncate(userAgent, 128);
    }

    private String firstIp(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String[] parts = raw.split(",");
        return parts.length == 0 ? raw.trim() : parts[0].trim();
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
