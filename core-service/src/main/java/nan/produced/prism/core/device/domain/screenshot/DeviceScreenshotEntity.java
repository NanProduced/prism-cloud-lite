package nan.produced.prism.core.device.domain.screenshot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "device_screenshot")
public class DeviceScreenshotEntity {

    @Id
    @Column(name = "screenshot_id", nullable = false, updatable = false)
    private UUID screenshotId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(name = "s3_key", nullable = false, length = 512, updatable = false)
    private String s3Key;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private Long sizeBytes;

    @Column(name = "content_type", length = 128, updatable = false)
    private String contentType;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;
}

