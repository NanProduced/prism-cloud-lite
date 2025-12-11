package nan.produced.prism.core.device.domain.customfield;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 映射 device_custom_field_value 表，用于存储每台设备的自定义字段值。
 *
 * @author Nan
 */
@Data
@Entity
@Table(name = "device_custom_field_value")
public class DeviceCustomFieldValueEntity {

    /**
     * 唯一 ID（雪花）
     */
    @Id
    @Column(name = "value_id")
    private Long valueId;

    /**
     * 归属用户/租户
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 设备 ID
     */
    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    /**
     * 自定义字段定义 ID
     */
    @Column(name = "field_id", nullable = false)
    private Long fieldId;

    /**
     * 文本/单选/URL/Email/Phone/Country 等字符串值
     */
    @Column(name = "value_text")
    private String valueText;

    /**
     * 数字类型值
     */
    @Column(name = "value_number")
    private BigDecimal valueNumber;

    /**
     * 日期时间类型值
     */
    @Column(name = "value_datetime")
    private OffsetDateTime valueDateTime;

    /**
     * 布尔类型值
     */
    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    /**
     * 多选类型值（存储选项 slug 数组）
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "value_multi_text", columnDefinition = "text[]")
    private String[] valueMultiText;

    /**
     * 额外 JSON（用于未来扩展，例如 phone 国家码等）
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value_json", columnDefinition = "jsonb")
    private String valueJson;

    @Column(name = "created_at")
    private OffsetDateTime createTime;

    @Column(name = "updated_at")
    private OffsetDateTime updateTime;
}
