package nan.produced.prism.core.media.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传路由配置属性
 *
 * Better Upload 协议支持多个路由，每个路由可以有不同的配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prism.media.upload")
public class UploadRouteProperties {

    /**
     * 路由配置映射
     * key: 路由名称（如 mediaLibrary, avatar）
     * value: 路由配置
     */
    private Map<String, RouteConfig> routes = new HashMap<>();

    /**
     * 单个路由的配置
     */
    @Getter
    @Setter
    public static class RouteConfig {

        /**
         * 允许的 MIME 类型列表
         * 支持通配符，如 "video/*", "image/*"
         */
        private List<String> allowedTypes = List.of("video/*", "image/*", "audio/*");

        /**
         * 单文件最大大小（字节）
         * 默认 5GB
         */
        private long maxFileSize = 5L * 1024 * 1024 * 1024;

        /**
         * 单次请求最大文件数量
         */
        private int maxFiles = 10;

        /**
         * 是否启用 Multipart 上传
         */
        private boolean multipartEnabled = true;

        /**
         * Multipart 分片大小（字节）
         * 默认 50MB，S3 要求最小 5MB
         */
        private long multipartPartSize = 50L * 1024 * 1024;

        /**
         * 触发 Multipart 上传的文件大小阈值（字节）
         * 默认 100MB
         */
        private long multipartThreshold = 100L * 1024 * 1024;

        /**
         * S3 存储路径前缀
         */
        private String pathPrefix = "media";

        /**
         * S3 存储类型
         */
        private String storageClass = "STANDARD";

        /**
         * 是否公开访问（建议 false，使用预签名 URL）
         */
        private boolean publicAccess = false;
    }

    /**
     * 获取指定路由的配置，如果不存在则返回默认配置
     */
    public RouteConfig getRouteConfig(String routeName) {
        return routes.getOrDefault(routeName, createDefaultConfig());
    }

    /**
     * 检查路由是否存在
     */
    public boolean hasRoute(String routeName) {
        return routes.containsKey(routeName);
    }

    private RouteConfig createDefaultConfig() {
        return new RouteConfig();
    }
}
