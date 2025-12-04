package nan.produced.prism.device.infrastracture.cache.local.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.dto.cache.DeviceAuthCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class CaffeineConfig {

    @Bean("deviceAuthenticationCache")
    @Primary
    public Cache<String, DeviceAuthCache> deviceAuthenticationCache() {
        return Caffeine.newBuilder()
                .maximumSize(20_000L)
                .expireAfterWrite(Duration.ofMinutes(30))
                .expireAfterAccess(Duration.ofMinutes(5))
                .recordStats() // 配合 Spring Boot Actuator + Prometheus 使用，在 Grafana 上监控缓存的命中率（Hit Rate）
                .build();

    }
}
