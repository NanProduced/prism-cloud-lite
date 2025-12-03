package nan.produced.prism.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.service.UserProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 用户资料控制器
 * 提供用户资料相关的 REST API 端点
 */
@Tag(name = "用户资料", description = "用户资料管理接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;

    /**
     * 获取当前用户资料
     * <p>
     * 重要: 此接口集成了 JIT Provisioning
     * - 如果用户资料不存在，会自动创建（首次登录）
     * - 后续调用直接返回已有资料
     * <p>
     * 前端应在用户登录成功后调用此接口获取用户信息
     *
     * @return 当前用户资料
     */
    @Operation(
        summary = "获取当前用户资料",
        description = """
            ### 功能说明
            获取当前登录用户的资料信息

            ### JIT Provisioning
            - 首次登录用户会自动创建资料（JIT Provisioning）
            - 后续访问直接返回已有资料
            - 默认昵称："用户" + 随机字符串

            ### 认证要求
            - 必须通过 Gateway 认证
            - Gateway 会自动添加 CLOUD_AUTH 头
            """
    )
    @ApiResponse(
        responseCode = "200",
        description = "成功返回用户资料",
        content = @Content(
            mediaType = "application/json",
            schema = @Schema(implementation = UserProfileDTO.class)
        )
    )
    @ApiResponse(
        responseCode = "401",
        description = "未认证（缺少 CLOUD_AUTH 头）"
    )
    @GetMapping("/me")
    public ResponseEntity<BffResponse<UserProfileDTO>> getCurrentUserProfile() {
        // JIT Provisioning: 获取或创建当前用户资料
        UserProfileEntity profile = userProfileService.getOrCreateCurrentUserProfile();

        // 转换为 DTO
        UserProfileDTO dto = toDTO(profile);

        BffResponse<UserProfileDTO> response =
            BffResponse.success(dto);

        return ResponseEntity.ok(response);
    }

    /**
     * 将实体转换为 DTO
     */
    private UserProfileDTO toDTO(UserProfileEntity entity) {
        return new UserProfileDTO(
            entity.getId(),
            entity.getPublicId(),
            entity.getEmail(),
            entity.getPhone(),
            entity.getDisplayName(),
            entity.getSubscriptionTier(),
            entity.getSubscriptionExpiresAt(),
            entity.getMetadata(),
            entity.getConfigs(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    /**
     * 用户资料 DTO
     */
    @Schema(description = "用户资料")
    public record UserProfileDTO(
        @Schema(description = "用户ID", example = "123e4567-e89b-12d3-a456-426614174000")
        UUID id,

        @Schema(description = "公开ID", example = "u_2Xk9P7qL")
        String publicId,

        @Schema(description = "邮箱地址", example = "user@example.com")
        String email,

        @Schema(description = "手机号", example = "13800138000")
        String phone,

        @Schema(description = "显示名称", example = "用户a3f5b2c9")
        String displayName,

        @Schema(description = "订阅层级", example = "FREE", allowableValues = {"FREE", "PRO"})
        String subscriptionTier,

        @Schema(description = "订阅过期时间")
        Instant subscriptionExpiresAt,

        @Schema(description = "元数据（JSON）")
        Map<String, Object> metadata,

        @Schema(description = "配置（JSON）")
        Map<String, Object> configs,

        @Schema(description = "创建时间")
        Instant createdAt,

        @Schema(description = "更新时间")
        Instant updatedAt
    ) {}
}
