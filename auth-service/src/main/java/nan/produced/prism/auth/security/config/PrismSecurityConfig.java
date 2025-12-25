package nan.produced.prism.auth.security.config;

import nan.produced.prism.auth.security.SecurityProps;
import nan.produced.prism.auth.security.authentication.PrismLoginAuthenticationProvider;
import nan.produced.prism.auth.security.login.PrismLoginInterface;
import nan.produced.prism.auth.security.login.config.PrismLoginAuthenticationSecurityConfig;
import nan.produced.prism.auth.security.otp.OtpProps;
import nan.produced.prism.auth.security.otp.PnvProps;
import nan.produced.prism.auth.security.rememberme.RememberMeAuthenticationFilter;
import nan.produced.prism.auth.security.signature.ServiceSignatureValidationFilter;
import nan.produced.prism.auth.security.login.handler.SpaRedirectAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({SecurityProps.class, OtpProps.class, PnvProps.class})
public class PrismSecurityConfig {

    private static final String LOGIN_PROPS_PREFIX = SecurityProps.PROPS_PREFIX + ".login";

    @Bean
    @ConditionalOnProperty(prefix = LOGIN_PROPS_PREFIX, name = "spa-login-page", havingValue = "true", matchIfMissing = true)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityProps securityProps,
                                                   SpaRedirectAuthenticationEntryPoint spaRedirectAuthenticationEntryPoint,
                                                   ServiceSignatureValidationFilter signatureValidationFilter,
                                                   RememberMeAuthenticationFilter rememberMeAuthenticationFilter,
                                                   @Qualifier("prismCorsConfig") CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
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
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(spaRedirectAuthenticationEntryPoint))
                // 添加服务签名验证过滤器（在 UsernamePasswordAuthenticationFilter 之前执行）
                .addFilterBefore(signatureValidationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rememberMeAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(prefix = LOGIN_PROPS_PREFIX, name = "spa-login-page", havingValue = "false")
    public SecurityFilterChain formLoginSecurityFilterChain(HttpSecurity http,
                                                            SecurityProps securityProps,
                                                            PrismLoginInterface loginService,
                                                            RequestCache requestCache,
                                                            ServiceSignatureValidationFilter signatureValidationFilter,
                                                            RememberMeAuthenticationFilter rememberMeAuthenticationFilter) throws Exception {
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
                .addFilterBefore(signatureValidationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rememberMeAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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

    @Bean
    public SpaRedirectAuthenticationEntryPoint spaRedirectAuthenticationEntryPoint(SecurityProps securityProps) {
        return new SpaRedirectAuthenticationEntryPoint(securityProps);
    }

    @Bean
    public AuthenticationProvider prismLoginAuthenticationProvider(PrismLoginInterface loginService) {
        return new PrismLoginAuthenticationProvider(loginService);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean("prismCorsConfig")
    @Primary
    public CorsConfigurationSource corsConfigurationSource(SecurityProps securityProps) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(securityProps.getLogin().getSpa().getAllowedOrigins());
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.addAllowedMethod(CorsConfiguration.ALL);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 登录/注册页面
        source.registerCorsConfiguration("/login/**", config);
        source.registerCorsConfiguration("/register/**", config);
        return source;
    }
}
