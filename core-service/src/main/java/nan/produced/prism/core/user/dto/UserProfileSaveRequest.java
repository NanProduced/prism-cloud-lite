package nan.produced.prism.core.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "保存当前用户资料（Profile Settings）")
public record UserProfileSaveRequest(

    @Schema(description = "显示名（不传则不修改）", maxLength = 80)
    String displayName,

    @Schema(description = "头像预设 ID（不传则不修改；平台不支持上传头像）", example = "avatar_01")
    String avatarId

) {}

