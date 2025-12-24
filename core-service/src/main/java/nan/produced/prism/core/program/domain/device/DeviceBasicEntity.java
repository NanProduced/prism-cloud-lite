package nan.produced.prism.core.program.domain.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Program 模块使用的设备基础信息映射（避免跨 Modulith 模块直接依赖 device 包）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pcc_device")
public class DeviceBasicEntity {

    @Id
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_name", nullable = false, length = 128)
    private String deviceName;

    @Column(name = "online_status")
    private Integer onlineStatus;
}
