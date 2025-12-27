package nan.produced.prism.gateway.security.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import nan.produced.prism.gateway.security.config.GatewaySecurityProps;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogoutStatusController {

    private final GatewaySecurityProps gatewaySecurityProps;

    public LogoutStatusController(GatewaySecurityProps gatewaySecurityProps) {
        this.gatewaySecurityProps = gatewaySecurityProps;
    }

    /**
     * 登出成功后的跳转
     * @param request 请求
     * @return 响应
     */
    @GetMapping({"/logout-status", "/logout_status"})
    public ResponseEntity<Void> logoutStatus(HttpServletRequest request) {
        String target = resolveSpaRedirectTarget(request);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, target).build();
    }

    private String resolveSpaRedirectTarget(HttpServletRequest request) {
        String target = request == null ? null : request.getParameter("redirect_uri");
        if (StringUtils.hasText(target) && isAllowedRedirectTarget(target)) {
            return target;
        }

        List<String> allowedOrigins = gatewaySecurityProps.getCors() == null ? null : gatewaySecurityProps.getCors().getAllowedOrigins();
        if (allowedOrigins != null && !allowedOrigins.isEmpty() && StringUtils.hasText(allowedOrigins.getFirst())) {
            return allowedOrigins.getFirst();
        }

        String host = gatewaySecurityProps.getOauth2() == null ? null : gatewaySecurityProps.getOauth2().getClient().getHost();
        return StringUtils.hasText(host) ? host : "/";
    }

    private boolean isAllowedRedirectTarget(String target) {
        try {
            URI uri = URI.create(target);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false;
            }
            String normalized = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() > 0) {
                normalized += ":" + uri.getPort();
            }
            List<String> allowedOrigins = gatewaySecurityProps.getCors() == null ? null : gatewaySecurityProps.getCors().getAllowedOrigins();
            String finalNormalized = normalized;
            return allowedOrigins != null && allowedOrigins.stream().anyMatch(it -> it != null && it.equalsIgnoreCase(finalNormalized));
        } catch (Exception ignore) {
            return false;
        }
    }
}

