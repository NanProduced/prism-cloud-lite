package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "API Key 视图")
public record UserApiKeyView(
    @Schema(description = "API Key ID（oauth2_registered_client.id）")
    String id,
    @Schema(description = "显示名称")
    String name,
    @Schema(description = "OAuth2 client_id")
    String clientId,
    @Schema(description = "OAuth2 client_secret（仅创建/重置时返回一次）")
    String clientSecret,
    @Schema(description = "创建时间")
    Instant createdAt,
    @Schema(description = "最后使用时间（暂未实现，可能为 null）")
    Instant lastUsedAt
) {
}

