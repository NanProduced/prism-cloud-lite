package nan.produced.prism.device.application.port.outbound.auth;

/**
 * 密码编码接口
 *
 * @author Nan
 */
public interface EncodePort {

    /**
     * 验证原始密码与编码密码是否匹配
     *
     * @param rawPassword 原始密码
     * @param encodedPassword 编码密码
     * @return 是否匹配
     */
    boolean matchesByPasswordEncoder(String rawPassword, String encodedPassword);

    /**
     * 编码原始密码
     *
     * @param rawPassword 原始密码
     * @return 编码后的密码
     */
    String encodeByPasswordEncoder(String rawPassword);
}
