package nan.produced.prism.core.device.domain.command;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pc_device_command_log")
public class DeviceCommandLog {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /**
     * 对应deviceCommand中的commandId
     */
    @Column(name = "operation_id", nullable = false)
    private String operationId;

    /**
     * 操作类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 32)
    private DeviceActionType actionType;

    /**
     * 追踪等级
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_level", nullable = false, length = 32)
    private DeviceActionTrackingLevel trackingLevel;

    /**
     * 指令执行状态 - 用于core-service追踪指令以使用SSE进行相关信息推送
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeviceCommandStatus status;

    /**
     * 指令详情 - action.body的json
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "ttl_minutes")
    private Long ttlMinutes;

    /**
     * 指令下发方式：Websocket/HTTP Cache
     */
    @Column(name = "send_method", length = 32)
    private String sendMethod;

    /**
     * device-service 内部指令ID（设备侧只支持 Integer）
     */
    @Column(name = "queued_id")
    private Integer queuedId;

    /**
     * 是否被 device-service 接受并进入投递流程
     */
    @Column(name = "accepted", nullable = false)
    private boolean accepted;

    /**
     * 指令是否覆盖了相同type指令
     */
    @Column(name = "covered", nullable = false)
    private boolean covered;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;


}
