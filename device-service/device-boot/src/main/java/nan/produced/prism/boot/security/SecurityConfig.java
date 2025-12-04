package nan.produced.prism.boot.security;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.boot.security.filter.DeviceBasicAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    @Bean
    public SecurityFilterChain deviceSecurityFilterChain(HttpSecurity http,
                                                         DeviceBasicAuthFilter deviceBasicAuthFilter) {

    }

    @Bean
    public DeviceBasicAuthFilter deviceBasicAuthFilter(AuthenticationManager authenticationManager) {
        return new DeviceBasicAuthFilter(authenticationManager);
    }
}
