package nan.produced.prism.auth.security.otp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 手机短信验证服码服务属性（Phone Number Verification）
 * <p>由阿里云提供</p>
 *
 * @author Nan
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = PnvProps.PROPS_PREFIX)
public class PnvProps {

    public static final String PROPS_PREFIX = "prism.pnv";

    /**
     * 验证码长度
     * <p>默认6位</p>
     */
    private long length = 6;

    /**
     * 验证码有效期（分钟）
     * <P>手机的验证码有效期只有5分钟</P>
     */
    private long validityMinutes = 5L;

    /**
     * 阿里云短信签名
     * <p>需认证，不可随便修改</p>
     */
    private String signName = "速通互联验证服务";

    private RateLimit rateLimit = new RateLimit();

    private PnvTemplate template = new PnvTemplate();

    private String templateParam = "{\"code\":\"##code##\",\"min\":\"" + validityMinutes + "\"}";

    @Data
    public static class RateLimit {
        /**
         * 最大尝试次数
         * <p>在时间窗口内允许的最大请求次数</p>
         */
        private int maxAttempts = 5;

        /**
         * 时间窗口（分钟）
         * <p>频率限制的时间窗口</p>
         */
        private long windowMinutes = 60L;
    }

    /**
     * 阿里云短信模板
     * <P>需认证，不可随便修改</P>
     */
    @Data
    public static class PnvTemplate {

        /**
         * 登录情况
         * <P>您的验证码为${code}。尊敬的客户，以上验证码${min}分钟内有效，请注意保密，切勿告知他人。</P>
         */
        private String login = "100001";

        /**
         * 绑定手机号情况
         * <P>尊敬的客户，您正在进行绑定手机号操作，您的验证码为${code}。以上验证码${min}分钟内有效，请注意保密，切勿告知他人。</P>
         */
        private String bind = "100004";

        /**
         * 密码重置情况
         * <p>尊敬的客户，您正在进行重置密码操作，您的验证码为${code}。以上验证码${min}分钟内有效，请注意保密，切勿告知他人。</p>
         */
        private String reset = "100003";

        /**
         * 通用情况
         * <p>您的验证码为：${code}，请勿泄露于他人！</p>
         */
        private String common = "SMS_329335025";
    }
}
