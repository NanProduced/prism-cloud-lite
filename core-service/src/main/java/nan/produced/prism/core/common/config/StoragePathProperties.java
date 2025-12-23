package nan.produced.prism.core.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 存储路径/命名相关配置（S3 key 与 VSN 内资源路径）。
 *
 * <p>用于收敛各模块的“魔法值”，便于在 yml 中统一调整与对照。</p>
 *
 * <p>放在 core.common 模块中，供 media/program 等模块共同引用，符合 Spring Modulith 依赖规范。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "prism.storage")
public class StoragePathProperties {

    /**
     * Media Library 上传与对象组织方式
     */
    private MediaLibrary mediaLibrary = new MediaLibrary();

    /**
     * Program 发布/草稿对象组织方式（vsn/cover 等）
     */
    private Program program = new Program();

    @Getter
    @Setter
    public static class MediaLibrary {

        /**
         * 内容寻址文件目录（md5+size 命名）
         */
        private String filesDir = "files";

        /**
         * 兜底上传目录（md5 缺失时使用）
         */
        private String uploadsDir = "uploads";
    }

    @Getter
    @Setter
    public static class Program {

        /**
         * Program 模块对象根前缀（S3 key）。
         * <p>例如：program</p>
         */
        private String rootPrefix = "program";

        /**
         * 发布版本目录名（S3 key）。
         * <p>例如：release</p>
         */
        private String releaseDir = "release";

        /**
         * 草稿目录名（S3 key）。
         * <p>例如：draft</p>
         */
        private String draftDir = "draft";

        /**
         * 发布版本号目录前缀（S3 key）。
         * <p>例如：v（最终目录为 v{version}）</p>
         */
        private String releaseVersionPrefix = "v";

        /**
         * VSN 内部资源目录（设备侧约定）
         * <p>例如：.\\_Res_.files\\</p>
         */
        private String vsnResFilesPath = ".\\_Res_.files\\";
    }
}

