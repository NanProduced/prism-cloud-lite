package nan.produced.prism.device.boot.security;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.boot.security.filter.DeviceBasicAuthFilter;
import nan.produced.prism.device.infrastracture.security.DeviceAuthenticationProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

@Configuration
@EnableConfigurationProperties(DeviceSecurityProps.class)
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain deviceSecurityFilterChain(HttpSecurity http,
                                                         DeviceSecurityProps securityProps,
                                                         DeviceBasicAuthFilter deviceBasicAuthFilter) throws Exception {
        return http
                // 设备端无需CSRF保护
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                // 设备无法保存session - 完全无状态
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // 防止页面被嵌入iframe，避免点击劫持攻击
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        // 启用MIME类型嗅探保护，防止MIME类型混淆攻击
                        .contentTypeOptions(Customizer.withDefaults())
                        // 配置HSTS，强制HTTPS连接
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000)  // 1年
                                .includeSubDomains(true))
                        // 禁用缓存敏感页面
                        .cacheControl(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(securityProps.getWhiteList().getIgnores().toArray(String[]::new)).permitAll()
                        .requestMatchers(securityProps.getDeviceApi()).authenticated()
                        .anyRequest().denyAll())
                .addFilterAfter(deviceBasicAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic
                        .authenticationEntryPoint(basicAuthenticationEntryPoint()))
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http,
                                                       DeviceAuthenticationProvider authenticationProvider) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.authenticationProvider(authenticationProvider);
        return authenticationManagerBuilder.build();
    }

    @Bean
    public BasicAuthenticationEntryPoint basicAuthenticationEntryPoint() {
        BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
        entryPoint.setRealmName("Prism-cloud-device-service");
        return entryPoint;
    }

    @Bean
    public DeviceBasicAuthFilter deviceBasicAuthFilter(AuthenticationManager authenticationManager) {
        return new DeviceBasicAuthFilter(authenticationManager);
    }

    /**
     * 密码编码器
     * <p>优化强度，针对随机生成的设备凭据进行性能优化</p>
     * <p>强度4对随机凭据提供充分安全性，同时大幅提升性能</p>
     * @return BCrypt密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4);
    }
}
