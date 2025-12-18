package nan.produced.prism.core.device.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "设备截图项")
public class DeviceScreenshotItemResp {

    @Schema(description = "截图ID")
    private UUID screenshotId;

    @Schema(description = "截图访问地址（CDN）")
    private String screenshotUrl;

    @Schema(description = "文件大小（bytes）")
    private Long sizeBytes;

    @Schema(description = "上传时间")
    private Instant uploadedAt;

    @Schema(description = "MIME 类型")
    private String contentType;
}

