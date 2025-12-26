package nan.produced.prism.gateway.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module;

@Configuration
public class SessionConfig implements BeanClassLoaderAware {

    private ClassLoader loader;

    @Bean("springSessionDefaultRedisSerializer")
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(SecurityJackson2Modules.getModules(this.loader));
        objectMapper.registerModule(new OAuth2ClientJackson2Module());
        objectMapper.activateDefaultTyping(polymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    private PolymorphicTypeValidator polymorphicTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.springframework.security.core")
                .allowIfSubType("org.springframework.security.web")
                .allowIfSubType("org.springframework.security.authentication")
                .allowIfSubType("org.springframework.security.oauth2")
                .allowIfSubType("nan.produced.prism.gateway")
                .allowIfSubType("java.lang")
                .allowIfSubType("java.util")
                .allowIfSubType("java.time")
                .allowIfSubType("java.net")
                .build();
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.loader = classLoader;
    }
}
