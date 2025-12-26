package nan.produced.prism.gateway.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.CoreJackson2Module;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.jackson2.OAuth2ClientJackson2Module;
import org.springframework.security.web.jackson2.WebJackson2Module;

@Configuration
public class SessionConfig implements BeanClassLoaderAware {

    private ClassLoader loader;

    @Bean("springSessionDefaultRedisSerializer")
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        ObjectMapper objectMapper = new ObjectMapper();

        // 1. 核心：注册模块，它们内部已经包含了对应的 Mixin
        objectMapper.registerModules(SecurityJackson2Modules.getModules(this.loader));
        objectMapper.registerModule(new CoreJackson2Module());
        objectMapper.registerModule(new WebJackson2Module());
        objectMapper.registerModule(new OAuth2ClientJackson2Module());

        // 2. 关键修改：不要对所有类型开启多态，而是针对 EVERYTHING，
        // 但使用更宽松的校验器，或者回归到不依赖白名单的旧方式
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        // 3. 忽略找不到 setter 的属性，这是解决 parameterMap 报错的直接手段
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // 避免 Jackson 在开启 DefaultTyping 时对 "setterless" 集合属性做 typed 反序列化（会触发 headerNames 等报错）
        objectMapper.configure(MapperFeature.USE_GETTERS_AS_SETTERS, false);

        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.loader = classLoader;
    }
}
