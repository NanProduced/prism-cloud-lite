package nan.produced.prism.core.device.domain.tags;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "device_tag")
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

    @Column(name = "color_id")
    private Long colorId;

    @Column(name = "icon_id")
    private Long iconId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updateTime;
}
