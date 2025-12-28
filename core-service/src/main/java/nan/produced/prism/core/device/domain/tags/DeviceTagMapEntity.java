package nan.produced.prism.core.device.domain.tags;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;

@Data
@Entity
@Table(name = "pcc_device_tag_map")
@IdClass(DeviceTagMapId.class)
public class DeviceTagMapEntity {

    @Id
    @Column(name = "device_id")
    private Long deviceId;

    @Id
    @Column(name = "tag_id")
    private Long tagId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id", insertable = false, updatable = false)
    private DeviceTagEntity tag;
}
