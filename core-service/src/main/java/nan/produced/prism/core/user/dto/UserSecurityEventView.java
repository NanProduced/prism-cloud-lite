package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "账号安全审计事件（Security History）")
public record UserSecurityEventView(
    @Schema(description = "事件ID")
    Long id,
    @Schema(description = "事件类型")
    String type,
    @Schema(description = "是否成功")
    boolean success,
    @Schema(description = "客户端IP（尽力获取）")
    String ipAddress,
    @Schema(description = "设备名称（尽力获取）")
    String deviceName,
    @Schema(description = "User-Agent（尽力获取）")
    String userAgent,
    @Schema(description = "扩展元数据（JSON 字符串）")
    String metadata,
    @Schema(description = "事件时间")
    Instant createdAt
) {
}

