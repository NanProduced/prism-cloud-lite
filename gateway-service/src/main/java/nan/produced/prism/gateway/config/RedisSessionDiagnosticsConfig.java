package nan.produced.prism.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.core.env.Environment;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

@Slf4j
@Configuration
public class RedisSessionDiagnosticsConfig {

    @Bean
    public ApplicationRunner redisSessionDiagnosticsRunner(
            Environment environment,
            ObjectProvider<SessionRepository<? extends Session>> sessionRepositoryProvider,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider
    ) {
        return args -> {
            log.info("Spring Session - active profiles: {}", String.join(",", environment.getActiveProfiles()));

            RedisConnectionFactory redisConnectionFactory = redisConnectionFactoryProvider.getIfAvailable();
            if (redisConnectionFactory == null) {
                log.warn("Spring Session - RedisConnectionFactory is missing; Redis-backed HttpSession will NOT work.");
            }
            else if (redisConnectionFactory instanceof LettuceConnectionFactory lettuce) {
                log.info("Spring Session - RedisConnectionFactory: Lettuce {}:{} db={}",
                        lettuce.getHostName(), lettuce.getPort(), lettuce.getDatabase());
            }
            else {
                log.info("Spring Session - RedisConnectionFactory: {}", redisConnectionFactory.getClass().getName());
            }

            SessionRepository<? extends Session> sessionRepository = sessionRepositoryProvider.getIfAvailable();
            if (sessionRepository == null) {
                log.warn("Spring Session - SessionRepository is missing; gateway is using container (in-memory) HttpSession.");
                return;
            }

            log.info("Spring Session - SessionRepository: {}", sessionRepository.getClass().getName());
        };
    }
}
