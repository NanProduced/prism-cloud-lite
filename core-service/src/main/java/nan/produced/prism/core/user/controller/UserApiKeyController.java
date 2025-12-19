package nan.produced.prism.core.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.user.dto.UserApiKeyCreateRequest;
import nan.produced.prism.core.user.dto.UserApiKeyView;
import nan.produced.prism.core.user.service.UserApiKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "API Keys（用户）", description = "Dashboard Settings - API Keys")
@RestController
@RequestMapping("/api/v1/user/api-keys")
@RequiredArgsConstructor
public class UserApiKeyController {

    private final UserApiKeyService userApiKeyService;

    @Operation(summary = "获取 API Keys 列表")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回 API Keys 列表",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = UserApiKeyView.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping
    public ResponseEntity<BffResponse<List<UserApiKeyView>>> listApiKeys() {
        List<UserApiKeyView> keys = userApiKeyService.listCurrentUserApiKeys();
        return ResponseEntity.ok(BffResponse.success(keys).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "创建 API Key", description = "仅在创建时返回一次 clientSecret，后续无法再次读取。")
    @ApiResponse(
        responseCode = "200",
        description = "创建成功",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserApiKeyView.class)))
    @PostMapping
    public ResponseEntity<BffResponse<UserApiKeyView>> createApiKey(@RequestBody UserApiKeyCreateRequest request) {
        UserApiKeyView created = userApiKeyService.createCurrentUserApiKey(request);
        return ResponseEntity.ok(BffResponse.success(created).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "重置 API Key secret", description = "重置成功后仅返回一次新 secret，旧 secret 立即失效。")
    @ApiResponse(
        responseCode = "200",
        description = "重置成功",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserApiKeyView.class)))
    @PostMapping("/{id}/regenerate")
    public ResponseEntity<BffResponse<UserApiKeyView>> regenerateSecret(@PathVariable("id") String id) {
        UserApiKeyView updated = userApiKeyService.regenerateCurrentUserApiKeySecret(id);
        return ResponseEntity.ok(BffResponse.success(updated).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "撤销 API Key", description = "撤销后该 key 无法再获取 token。")
    @ApiResponse(responseCode = "200", description = "撤销成功")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<BffResponse<Object>> revokeApiKey(@PathVariable("id") String id) {
        userApiKeyService.revokeCurrentUserApiKey(id);
        return ResponseEntity.ok(BffResponse.success().withTraceId(TraceUtils.getTraceId()));
    }
}

