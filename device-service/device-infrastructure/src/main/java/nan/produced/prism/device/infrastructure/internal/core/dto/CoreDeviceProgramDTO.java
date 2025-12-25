package nan.produced.prism.device.infrastructure.internal.core.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * core-service internal /internal/devices/{deviceId}/programs 响应 DTO（device-service侧）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoreDeviceProgramDTO {
    private Integer deviceProgramId;
    private String title;
    private OffsetDateTime createdAt;
    private OffsetDateTime assignedAt;
}

