package nan.produced.prism.device.application.dto.cache;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.device.application.domain.device.DeviceAccountStatus;

import java.io.Serial;
import java.io.Serializable;

/**
 * 终端认证缓存DTO
 * 用于缓存终端设备的认证信息，支持WebSocket和HTTP Basic Auth场景
 *
 * @author Nan
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeviceAuthCache implements Serializable {

    @Serial
    private static final long serialVersionUID = 1055365662261629581L;

    /**
     * 设备ID - 唯一标识
     */
    private Long deviceId;

    /**
     * 账户名称 - 认证用户名
     */
    private String accountName;

    /**
     * 凭据哈希值 - SHA-256快速验证，格式: SHA-256(username:password)
     */
    private String credentialsHash;

    /**
     * 账户状态
     */
    private DeviceAccountStatus accountStatus;
}
