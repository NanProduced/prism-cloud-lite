package nan.produced.prism.core.admin.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "管理端用户搜索项")
public record AdminUserSearchItemView(
    @Schema(description = "用户 publicId")
    String publicId,
    @Schema(description = "邮箱")
    String email,
    @Schema(description = "手机号")
    String phone,
    @Schema(description = "状态（ACTIVE/LOCKED/DELETED）")
    String status,
    @Schema(description = "创建时间")
    Instant createdAt
) {
}

