package nan.produced.prism.application.domain.device;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DeviceAccount {

    /**
     * 设备Id
     */
    private Long deviceId;

    /**
     * 账号名称
     */
    private String accountName;

    /**
     * 密码
     */
    private String passwordHash;

    /**
     * 账号状态
     */
    private DeviceAccountStatus status;

    /**
     * 上云时间
     */
    private LocalDateTime firstLoginTime;

    /**
     * 最后连接时间
     */
    private LocalDateTime lastLoginTime;

    /**
     * 最后连接IP
     */
    private String lastLoginIp;
}
