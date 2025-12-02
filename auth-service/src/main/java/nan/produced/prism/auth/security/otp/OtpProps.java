package nan.produced.prism.auth.security.otp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

@Data
@RefreshScope
@ConfigurationProperties(prefix = OtpProps.PROPS_PREFIX)
public class OtpProps {

    public static final String PROPS_PREFIX = "prism.otp";

    /**
     * OTP 验证码长度
     * 默认 6 位
     */
    private int length = 6;

    /**
     * OTP 有效期（分钟）
     * 默认 10 分钟
     */
    private long validityMinutes = 10L;

    /**
     * 频率限制配置
     */
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class RateLimit {

        /**
         * 最大尝试次数
         * 在时间窗口内允许的最大请求次数
         */
        private int maxAttempts = 5;

        /**
         * 时间窗口（分钟）
         * 频率限制的时间窗口
         */
        private long windowMinutes = 60L;
    }

}
