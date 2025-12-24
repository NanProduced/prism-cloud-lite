package nan.produced.prism.core.device.domain.customfield;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "pcc_device_custom_field_def")
public class DeviceCustomFieldDefEntity {

    /**
     * 雪花算法生成的唯一ID
     */
    @Id
    @Column(name = "field_id")
    private Long fieldId;

    /**
     * 设备自定义字段定义所属用户
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 内部的稳定标识
     */
    @Column(name = "field_key", nullable = false, length = 128)
    private String fieldKey;

    /**
     * 自定义字段定义类型
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 32)
    private CustomFieldType fieldType;

    /**
     * 自定义字段定义名称
     */
    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /**
     * 自定义字段定义描述
     */
    @Column(name = "description")
    private String description;

    /**
     * 图标（前端 Lucide icon name，可选）
     */
    @Column(name = "icon", length = 64)
    private String icon;

    /**
     * 是否需要付费订阅
     */
    @Column(name = "plan_tier_required", nullable = false)
    private Boolean planTierRequired;

    /**
     * 自定义字段定义序号
     */
    @Column(name = "sequence_no")
    private Integer sequence;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createTime;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updateTime;

    @OneToMany(mappedBy = "field", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DeviceCustomFieldOptionEntity> options = new ArrayList<>();
}
