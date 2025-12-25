package nan.produced.prism.device.infrastructure.internal.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * core-service internal /internal/devices/{deviceId}/programs/{deviceProgramId}/media 响应 DTO（device-service侧）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoreDeviceProgramMediaDTO {
    private String url;
    private Long sizeBytes;
}

