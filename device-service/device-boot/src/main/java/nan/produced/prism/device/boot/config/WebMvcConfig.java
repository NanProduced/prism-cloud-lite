package nan.produced.prism.device.boot.config;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.boot.interceptor.DeviceStatusUpdateInterceptor;
import nan.produced.prism.device.boot.security.DeviceSecurityProps;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final DeviceStatusUpdateInterceptor deviceStatusUpdateInterceptor;
    private final DeviceSecurityProps deviceSecurityProps;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String deviceApiPattern = deviceSecurityProps.getDeviceApi();
        if (deviceApiPattern == null || deviceApiPattern.isBlank()) {
            deviceApiPattern = "/wp-json/**";
        }

        registry.addInterceptor(deviceStatusUpdateInterceptor)
                .addPathPatterns(deviceApiPattern);
    }
}

