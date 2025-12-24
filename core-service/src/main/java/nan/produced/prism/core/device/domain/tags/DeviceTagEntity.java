package nan.produced.prism.core.device.domain.tags;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 设备标签实体
 *
 * @author Nan
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pcc_device_tag", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_tag_user_slug", columnNames = {"user_id", "slug"})
})
public class DeviceTagEntity {

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    @Column(name = "tag_name", nullable = false, length = 128)
    private String tagName;

    @Column(name = "description")
    private String description;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "slug", nullable = false, length = 128)
    private String slug;

    /**
     * 颜色 - 后端不处理，由前端控制映射，后端仅存储颜色值
     */
    @Column(name = "color")
    private String color;

    /**
     * 图标 - 后端不处理，由前端控制映射，后端仅存储图标值
     */
    @Column(name = "icon")
    private String icon;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updateTime;
}
