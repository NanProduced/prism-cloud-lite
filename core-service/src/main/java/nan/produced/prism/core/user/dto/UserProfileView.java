package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "前端展示的用户资料")
public record UserProfileView(
    @Schema(description = "用户 publicId")
    String publicId,
    @Schema(description = "邮箱")
    String email,
    @Schema(description = "显示名")
    String displayName,
    @Schema(description = "头像预设 ID（平台不支持上传头像）")
    String avatarId,
    @Schema(description = "套餐等级")
    String subscriptionTier,
    @Schema(description = "套餐到期时间")
    Instant subscriptionExpiresAt,
    @Schema(description = "手机号")
    String phone
) {}
