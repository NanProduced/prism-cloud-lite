package nan.produced.prism.infrastracture.persistence.postgre.entity;

import java.time.LocalDateTime;

public class DeviceAccountEntity {

    /**
     * 主键ID
     * 雪花算法生成
     */
    private Long deviceId;

    private String account;

    /**
     * 设备密码 - BCrypt 加密
     */
    private String password;

    private Byte accountStatus;

    private LocalDateTime firstLoginTime;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
