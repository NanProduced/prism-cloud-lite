package nan.produced.prism.core.assistant.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.api.dto.UpsertUserAiModelConfigReq;
import nan.produced.prism.core.assistant.api.dto.UserAiModelConfigResp;
import nan.produced.prism.core.assistant.application.credentials.UserAiModelConfigService;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "AI Assistant", description = "用户 AI 模型配置（BYOK：OpenAI/Gemini 等）")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/assistant/model-configs")
public class UserAiModelConfigController {

    private final UserAiModelConfigService service;

    @Operation(summary = "列出用户 AI 模型配置", description = "返回当前用户已保存的 BYOK 配置列表（provider/model/enabled/default/apiKey 是否存在等）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回配置列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = UserAiModelConfigResp.class))))
    @ApiResponse(responseCode = "401", description = "未登录（CLOUD_AUTH 头缺失/无效或会话失效）")
    @GetMapping
    public BffResponse<List<UserAiModelConfigResp>> list() {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        List<UserAiModelConfigResp> data = service.listForUser(userId).stream()
                .map(v -> new UserAiModelConfigResp(v.provider(), v.model(), v.enabled(), v.isDefault(), v.hasApiKey(), v.apiKeyLast4()))
                .toList();
        return new BffResponse<>(true, data, null, TraceUtils.getTraceId());
    }

    @Operation(summary = "新增/更新用户 AI 模型配置", description = "Upsert 指定 provider 的配置；可同时更新 model/enabled/apiKey，并可设置为 default。")
    @ApiResponse(responseCode = "200", description = "成功保存（BffResponse.success=true）")
    @ApiResponse(responseCode = "400", description = "参数校验失败或 provider 不支持")
    @ApiResponse(responseCode = "401", description = "未登录（CLOUD_AUTH 头缺失/无效或会话失效）")
    @PostMapping
    public BffResponse<Void> upsert(@Valid @RequestBody UpsertUserAiModelConfigReq req) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        service.upsert(
                userId,
                req.provider(),
                req.model(),
                req.enabled() == null || req.enabled(),
                req.makeDefault() != null && req.makeDefault(),
                req.apiKey()
        );
        return new BffResponse<>(true, null, null, TraceUtils.getTraceId());
    }

    @Operation(summary = "设置默认 provider", description = "把指定 provider 设置为默认使用的模型提供方（会清空其它 provider 的默认标记）。")
    @ApiResponse(responseCode = "200", description = "成功设置默认 provider（BffResponse.success=true）")
    @ApiResponse(responseCode = "400", description = "provider 不支持")
    @ApiResponse(responseCode = "401", description = "未登录（CLOUD_AUTH 头缺失/无效或会话失效）")
    @PostMapping("/{provider}/default")
    public BffResponse<Void> setDefault(
            @Parameter(
                    description = "模型提供方",
                    schema = @Schema(allowableValues = {"local-vllm", "openai", "gemini"}))
            @PathVariable("provider") String provider) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        service.setDefault(userId, provider);
        return new BffResponse<>(true, null, null, TraceUtils.getTraceId());
    }

    @Operation(summary = "删除 provider 配置", description = "删除指定 provider 的配置（仅影响当前用户）。")
    @ApiResponse(responseCode = "200", description = "成功删除（BffResponse.success=true）")
    @ApiResponse(responseCode = "400", description = "provider 不支持")
    @ApiResponse(responseCode = "401", description = "未登录（CLOUD_AUTH 头缺失/无效或会话失效）")
    @DeleteMapping("/{provider}")
    public BffResponse<Void> delete(
            @Parameter(
                    description = "模型提供方",
                    schema = @Schema(allowableValues = {"local-vllm", "openai", "gemini"}))
            @PathVariable("provider") String provider) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        service.delete(userId, provider);
        return new BffResponse<>(true, null, null, TraceUtils.getTraceId());
    }
}
