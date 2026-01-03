package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "当前管理员信息")
public record AdminMeView(
    @Schema(description = "publicId（JWT sub）")
    String publicId,
    @Schema(description = "Auth-service 内部 UUID（JWT user_uuid）")
    String userUuid,
    @Schema(description = "角色列表（JWT roles）")
    List<String> roles,
    @Schema(description = "订阅层级（JWT tier，admin 一般为 null）")
    String tier
) {
}

