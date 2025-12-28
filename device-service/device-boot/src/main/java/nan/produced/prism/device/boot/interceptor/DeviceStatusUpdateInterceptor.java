package nan.produced.prism.device.boot.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.status.ReportSource;
import nan.produced.prism.device.application.port.inbound.status.DeviceOnlineStatusUseCase;
import nan.produced.prism.device.infrastructure.security.DevicePrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 设备状态更新拦截器
 * <P>自动拦截设备HTTP请求并更新在线状态</P>
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceStatusUpdateInterceptor implements HandlerInterceptor {

    private final DeviceOnlineStatusUseCase deviceOnlineStatusUseCase;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return true;
            }

            Object principal = authentication.getPrincipal();

            if (principal instanceof DevicePrincipal devicePrincipal) {
                Long deviceId = devicePrincipal.getDeviceId();
                String clientIp = getClientIp(request);
                // 更新设备最后上报时间
                deviceOnlineStatusUseCase.updateLastReportTime(deviceId, ReportSource.HTTP, clientIp);
            }

        } catch (Exception e) {
            log.error("HTTP - 设备状态更新失败", e);
        }

        return true;
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIp(HttpServletRequest request) {
        // 依次检查各种代理头
        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
                "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR"
        };

        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 取第一个IP（可能是逗号分隔的多个IP）
                return ip.split(",")[0].trim();
            }
        }

        // 最后使用 RemoteAddr
        return request.getRemoteAddr();
    }
}
