package nan.produced.prism.auth.security.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import nan.produced.prism.auth.security.principal.PrismUserPrincipal;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SessionConfig implements BeanClassLoaderAware {

    private ClassLoader loader;

    @Bean
    public ObjectMapper prismSecurityObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(SecurityJackson2Modules.getModules(this.loader));
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        objectMapper.addMixIn(PrismUserPrincipal.class, UserPrincipalMixin.class);
        objectMapper.activateDefaultTyping(polymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        return objectMapper;
    }

    /**
     * 替换默认的Redis序列化器
     *
     * @param prismSecurityObjectMapper   ObjectMapper
     * @return Redis序列化器
     */
    @Bean("springSessionDefaultRedisSerializer")
    public RedisSerializer<Object> springSessionDefaultRedisSerializer(ObjectMapper prismSecurityObjectMapper) {
        return new GenericJackson2JsonRedisSerializer(prismSecurityObjectMapper);
    }

    private PolymorphicTypeValidator polymorphicTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("org.springframework.security.core")
                .allowIfSubType("org.springframework.security.web")
                .allowIfSubType("nan.produced.prism.auth")
                .allowIfSubType("org.springframework.security.authentication")
                .allowIfSubType("org.springframework.security.oauth2")
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

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class")
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static abstract class UserPrincipalMixin {
    }
}
