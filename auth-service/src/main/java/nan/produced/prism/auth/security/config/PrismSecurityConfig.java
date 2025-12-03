package nan.produced.prism.auth.security.config;

import nan.produced.prism.auth.security.SecurityProps;
import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.login.config.PrismLoginAuthenticationSecurityConfig;
import nan.produced.prism.auth.security.otp.OtpProps;
import nan.produced.prism.auth.security.signature.ServiceSignatureValidationFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

@Configuration
@EnableConfigurationProperties({SecurityProps.class, OtpProps.class})
public class PrismSecurityConfig {

    @Bean
    public SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http,
                                                            SecurityProps securityProps,
                                                            PrismLoginInterface loginService,
                                                            RequestCache requestCache,
                                                            ServiceSignatureValidationFilter signatureValidationFilter) throws Exception {
        http
                .with(new PrismLoginAuthenticationSecurityConfig(loginService, securityProps, requestCache), Customizer.withDefaults())
                .authorizeHttpRequests(request ->  request
                        .requestMatchers(securityProps.getLogin().getLoginPageUrl()).permitAll()
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers(securityProps.getWhiteList().getRsaPublicKey()).permitAll()
                        .requestMatchers(securityProps.getWhiteList().getActuator()).permitAll()
                        .requestMatchers(securityProps.getWhiteList().getIgnoreUrls()).permitAll()
                        .requestMatchers(securityProps.getWhiteList().getSwagger()).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(securityProps.getWhiteList().getIgnoreUrls())
                        .ignoringRequestMatchers(securityProps.getLogin().getLoginProcessingUrl()))
                .formLogin(formLogin -> formLogin
                        .loginPage(securityProps.getLogin().getLoginPageUrl())
                        .loginProcessingUrl(securityProps.getLogin().getLoginProcessingUrl())
                        .defaultSuccessUrl(securityProps.getLogin().getLoginSuccessUrl()))
                // 添加服务签名验证过滤器（在 UsernamePasswordAuthenticationFilter 之前执行）
                .addFilterBefore(signatureValidationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestCache requestCache() {
        return new HttpSessionRequestCache();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
