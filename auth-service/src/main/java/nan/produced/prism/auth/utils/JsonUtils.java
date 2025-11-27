package nan.produced.prism.auth.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.core.serializer.support.SerializationFailedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JsonUtils {

    private JsonUtils() {
        throw new UnsupportedOperationException();
    }

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        List<Module> modules = new ArrayList<>(1);
        try {
            Class.forName("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule");
            modules.add(new JavaTimeModule());
        } catch (ClassNotFoundException ignore) {
        }

        OBJECT_MAPPER = new ObjectMapper()
                // 时区偏移
                .registerModules(modules)
                // 反序列化时有未知属性不报错
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // 序列化属性不对应不报错
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                // 序列化时禁止时间Date按时间戳格式序列化
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // 允许Json的key没有双引号
                .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                // 允许Json有单引号
                .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
                // 忽略字段值为null的字段（为null的字段不参加序列化）
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public static String toJson(Object obj) {
        if (Objects.isNull(obj)) {
            return "";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new SerializationFailedException("Serialize failed", e);
        }
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
