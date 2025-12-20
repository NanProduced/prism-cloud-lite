package nan.produced.prism.device.infrastructure.persistence.postgre.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "pcd_device_account")
public class DeviceAccountEntity {

    /**
     * 主键ID
     * 雪花算法生成（外部生成）
     */
    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "account", nullable = false, length = 64, unique = true)
    private String account;

    /**
     * 设备密码 - BCrypt 加密
     */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "account_status", nullable = false)
    private Byte accountStatus;

    @Column(name = "first_login_time")
    private LocalDateTime firstLoginTime;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
    }

    @PreUpdate
    void preUpdate() {
        updateTime = LocalDateTime.now();
    }
}
