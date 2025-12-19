package nan.produced.prism.auth.security.rememberme;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.auth.domain.session.RememberMeTokenEntity;
import nan.produced.prism.auth.domain.session.repository.RememberMeTokenRepository;
import nan.produced.prism.auth.domain.user.UserType;
import nan.produced.prism.auth.domain.user.repository.EndUserRepository;
import nan.produced.prism.auth.security.SecurityProps;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Slf4j
@Service
@RequiredArgsConstructor
public class RememberMeTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RememberMeTokenRepository tokenRepository;
    private final EndUserRepository endUserRepository;
    private final SecurityProps securityProps;

    /**
     * 处理用户登录成功后的“记住我”功能逻辑
     *
     * @param request   HTTP请求对象
     * @param response  HTTP响应对象
     * @param principal 当前认证用户的主体信息
     * @param rememberMe 是否启用记住我功能
     */
    @Transactional
    public void handleLoginSuccess(HttpServletRequest request,
                                   HttpServletResponse response,
                                   PrismUserPrincipal principal,
                                   boolean rememberMe) {
        SecurityProps.Login.RememberMe props = securityProps.getLogin().getRememberMe();
        if (!props.isEnabled() || principal == null) {
            clearRememberMeCookie(response);
            return;
        }
        if (!rememberMe) {
            clearRememberMeCookie(response);
            return;
        }

        RememberMeTokenEntity entity = new RememberMeTokenEntity();
        entity.setUserId(principal.getId());
        entity.setUserType(principal.getUserType());
        entity.setSeries(randomToken(32));
        String rawToken = randomToken(64);
        entity.setTokenHash(hash(rawToken));
        Instant now = Instant.now();
        entity.setLastUsedAt(now);
        entity.setExpiresAt(now.plus(props.getValidityDays(), ChronoUnit.DAYS));
        entity.setDeviceName(resolveDeviceName(request));
        entity.setUserAgent(truncate(request.getHeader("User-Agent"), 512));
        entity.setIpAddress(resolveClientIp(request));
        tokenRepository.save(entity);
        writeCookie(response, entity.getSeries(), rawToken, props);
    }

    @Transactional
    public Optional<Authentication> autoLogin(HttpServletRequest request, HttpServletResponse response) {
        SecurityProps.Login.RememberMe props = securityProps.getLogin().getRememberMe();
        if (!props.isEnabled()) {
            return Optional.empty();
        }
        RememberMeCookie cookie = extractCookie(request, props.getCookieName()).orElse(null);
        if (cookie == null) {
            return Optional.empty();
        }
        Optional<RememberMeTokenEntity> entityOpt = tokenRepository.findBySeriesAndRevokedFalse(cookie.series());
        if (entityOpt.isEmpty()) {
            clearRememberMeCookie(response);
            return Optional.empty();
        }
        RememberMeTokenEntity entity = entityOpt.get();
        Instant now = Instant.now();
        if (entity.isExpired(now) || !matches(cookie.token(), entity.getTokenHash())) {
            entity.setRevoked(true);
            entity.setRevokedAt(now);
            tokenRepository.save(entity);
            clearRememberMeCookie(response);
            return Optional.empty();
        }
        PrismUserPrincipal principal = loadPrincipal(entity);
        if (principal == null) {
            entity.setRevoked(true);
            entity.setRevokedAt(now);
            tokenRepository.save(entity);
            clearRememberMeCookie(response);
            return Optional.empty();
        }

        String newToken = randomToken(64);
        entity.setTokenHash(hash(newToken));
        entity.setLastUsedAt(now);
        tokenRepository.save(entity);
        writeCookie(response, entity.getSeries(), newToken, props);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        return Optional.of(authentication);
    }

    @Transactional(readOnly = true)
    public List<RememberedDeviceView> listActiveTokens(UUID userId, String activeSeries) {
        Instant now = Instant.now();
        List<RememberMeTokenEntity> entities = tokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId);
        return entities.stream()
                .filter(entity -> !entity.isExpired(now))
                .map(entity -> new RememberedDeviceView(
                        entity.getSeries(),
                        entity.getDeviceName(),
                        entity.getIpAddress(),
                        entity.getUserAgent(),
                        entity.getCreatedAt(),
                        entity.getLastUsedAt(),
                        entity.getExpiresAt(),
                        entity.getSeries().equals(activeSeries)
                ))
                .toList();
    }

    @Transactional
    public void revokeToken(UUID userId, String series) {
        if (!StringUtils.hasText(series)) {
            return;
        }
        tokenRepository.findBySeriesAndRevokedFalse(series)
                .filter(token -> token.getUserId().equals(userId))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    token.setRevokedAt(Instant.now());
                    tokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokenRepository.revokeAll(userId, Instant.now());
    }

    public Optional<String> extractSeriesFromCookie(HttpServletRequest request) {
        SecurityProps.Login.RememberMe props = securityProps.getLogin().getRememberMe();
        return extractCookie(request, props.getCookieName()).map(RememberMeCookie::series);
    }

    public void clearRememberMeCookie(HttpServletResponse response) {
        SecurityProps.Login.RememberMe props = securityProps.getLogin().getRememberMe();
        Cookie cookie = new Cookie(props.getCookieName(), "");
        cookie.setPath(props.getCookiePath());
        cookie.setHttpOnly(true);
        cookie.setSecure(props.isSecureCookie());
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private PrismUserPrincipal loadPrincipal(RememberMeTokenEntity entity) {
        if (entity.getUserType() != UserType.END_USER) {
            return null;
        }
        return endUserRepository.findById(entity.getUserId())
                .map(endUser -> PrismUserPrincipal.fromEndUser(endUser, List.of("ROLE_END_USER"), null, null))
                .orElse(null);
    }

    private void writeCookie(HttpServletResponse response,
                             String series,
                             String token,
                             SecurityProps.Login.RememberMe props) {
        String value = series + ":" + token;
        Cookie cookie = new Cookie(props.getCookieName(), value);
        cookie.setPath(props.getCookiePath());
        cookie.setHttpOnly(true);
        cookie.setSecure(props.isSecureCookie());
        long validitySeconds = props.getValidityDays() * 24 * 60 * 60;
        cookie.setMaxAge((int) Math.min(Integer.MAX_VALUE, validitySeconds));
        response.addCookie(cookie);
    }

    private Optional<RememberMeCookie> extractCookie(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                String[] parts = cookie.getValue().split(":");
                if (parts.length == 2 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])) {
                    return Optional.of(new RememberMeCookie(parts[0], parts[1]));
                }
            }
        }
        return Optional.empty();
    }

    private boolean matches(String rawToken, String storedHash) {
        return hash(rawToken).equals(storedHash);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        }
        catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveDeviceName(HttpServletRequest request) {
        String custom = request.getHeader("X-Client-Device");
        if (StringUtils.hasText(custom)) {
            return truncate(custom, 128);
        }
        return truncate(request.getHeader("User-Agent"), 128);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public record RememberedDeviceView(
            String series,
            String deviceName,
            String ipAddress,
            String userAgent,
            Instant createdAt,
            Instant lastUsedAt,
            Instant expiresAt,
            boolean current) {
    }

    private record RememberMeCookie(String series, String token) {
    }
}
