package nan.produced.prism.core.device.domain.customfield;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Data;

/**
 * 单选/多选字段的选项定义。
 */
@Data
@Entity
@Table(name = "device_custom_field_option")
public class DeviceCustomFieldOptionEntity {

    @Id
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private DeviceCustomFieldDefEntity field;

    /** 标识或 slug，便于存储到 value_text/value_multi_text 中 */
    @Column(name = "option_key", nullable = false, length = 128)
    private String optionKey;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "description")
    private String description;

    @Column(name = "sequence_no")
    private Integer sequence;

    @Column(name = "active", nullable = false)
    private Boolean active;

    /**
     * 颜色（preset key 或 hex，可选）
     */
    @Column(name = "color", length = 32)
    private String color;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createTime;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updateTime;
}
