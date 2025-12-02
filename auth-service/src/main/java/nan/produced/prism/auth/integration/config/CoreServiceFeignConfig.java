package nan.produced.prism.auth.integration.config;

import nan.produced.prism.auth.security.signature.ServiceSignatureInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core-Service Feign 客户端配置
 * 用于注册服务签名拦截器
 */
@Configuration
public class CoreServiceFeignConfig {

    /**
     * 注册服务签名拦截器
     * 用于为调用 Core-Service 的请求添加 X-Service-From、X-Timestamp、X-Signature 头
     */
    @Bean
    public ServiceSignatureInterceptor serviceSignatureInterceptor() {
        return new ServiceSignatureInterceptor();
    }
}
