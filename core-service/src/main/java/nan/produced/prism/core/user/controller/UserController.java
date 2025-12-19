package nan.produced.prism.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.user.converter.UserProfileConverter;
import nan.produced.prism.core.user.domain.UserProfileEntity;
import nan.produced.prism.core.user.dto.UserProfileSaveRequest;
import nan.produced.prism.core.user.dto.UserProfileView;
import nan.produced.prism.core.user.dto.UserSettingsOverridesView;
import nan.produced.prism.core.user.service.UserProfileService;
import nan.produced.prism.core.user.service.UserSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 用户资料接口
 */
@Tag(name = "用户资料", description = "用户资料相关接口")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final UserProfileConverter userProfileConverter;
    private final UserSettingsService userSettingsService;

    @Operation(
        summary = "获取当前用户资料",
        description = "调用方必须通过 Gateway 鉴权，首次登录会触发 JIT Provisioning")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回用户资料",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserProfileView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/me")
    public ResponseEntity<BffResponse<UserProfileView>> getCurrentUserProfile() {
        UserProfileEntity profile = userProfileService.getOrCreateCurrentUserProfile();
        UserProfileView view = userProfileConverter.toView(profile);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
        summary = "保存当前用户资料（Profile）",
        description = """
            支持更新显示名与头像预设（avatarId）。
            - avatarId 为前端预置头像 ID，平台不支持上传头像；
            - 未传入的字段不会修改。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功返回更新后的用户资料",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserProfileView.class)))
    @ApiResponse(responseCode = "400", description = "请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/me")
    public ResponseEntity<BffResponse<UserProfileView>> saveCurrentUserProfile(@RequestBody UserProfileSaveRequest request) {
        UserProfileEntity profile = userProfileService.saveCurrentUserProfile(request);
        UserProfileView view = userProfileConverter.toView(profile);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
        summary = "获取当前用户 Settings 覆盖值",
        description = "仅返回已保存的 overrides；默认值由前端自行合并渲染。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回用户 Settings 覆盖值",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserSettingsOverridesView.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/settings")
    public ResponseEntity<BffResponse<UserSettingsOverridesView>> getCurrentUserSettingsOverrides() {
        UserSettingsOverridesView view = userSettingsService.getCurrentUserSettingsOverrides();
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(
        summary = "更新当前用户 Settings 覆盖值（Merge Patch）",
        description = """
            使用 JSON Merge Patch 语义更新 overrides：
            - 传入对象字段：深度合并；
            - 字段值为 null：删除 key（用于恢复默认值）；
            - 本接口不会自动补默认值。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功更新并返回最新 Settings 覆盖值",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserSettingsOverridesView.class)))
    @ApiResponse(responseCode = "400", description = "请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/settings")
    public ResponseEntity<BffResponse<UserSettingsOverridesView>> patchCurrentUserSettingsOverrides(@RequestBody ObjectNode patch) {
        UserSettingsOverridesView view = userSettingsService.patchCurrentUserSettingsOverrides(patch);
        return ResponseEntity.ok(BffResponse.success(view).withTraceId(TraceUtils.getTraceId()));
    }
}
