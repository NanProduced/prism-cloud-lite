package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "账号安全 - 活跃会话（remember-me 设备）")
public record UserActiveSessionView(
    @Schema(description = "设备会话标识（remember-me series）")
    String series,
    @Schema(description = "设备名称（前端可自行做更友好的 UA 解析）")
    String deviceName,
    @Schema(description = "最近一次使用的 IP 地址")
    String ipAddress,
    @Schema(description = "User-Agent 原始值")
    String userAgent,
    @Schema(description = "创建时间")
    Instant createdAt,
    @Schema(description = "最近使用时间")
    Instant lastUsedAt,
    @Schema(description = "到期时间")
    Instant expiresAt,
    @Schema(description = "是否为当前设备")
    boolean current
) {}

