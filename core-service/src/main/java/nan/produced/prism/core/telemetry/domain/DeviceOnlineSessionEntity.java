package nan.produced.prism.core.telemetry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 设备在线会话（上线-下线区间）事实数据
 *
 * <p>说明：</p>
 * <ul>
 *   <li>所有时间统一存储为 UTC（TIMESTAMPTZ）</li>
 *   <li>range 列 period 为数据库生成列，用于 GiST overlap 过滤（不在实体中映射）</li>
 * </ul>
 *
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "pcc_device_online_session",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_device_online_session_unique",
                        columnNames = {"device_id", "online_at", "offline_at"}
                )
        }
)
public class DeviceOnlineSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "online_at", nullable = false)
    private OffsetDateTime onlineAt;

    @Column(name = "offline_at", nullable = false)
    private OffsetDateTime offlineAt;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
