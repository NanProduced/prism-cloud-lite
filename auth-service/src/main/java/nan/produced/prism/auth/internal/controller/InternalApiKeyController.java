package nan.produced.prism.auth.internal.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.auth.common.exception.BizException;
import nan.produced.prism.auth.common.response.ApiResponse;
import nan.produced.prism.auth.internal.dto.InternalApiKeyCreateRequest;
import nan.produced.prism.auth.internal.dto.InternalApiKeyView;
import nan.produced.prism.auth.internal.service.InternalApiKeyService;
import nan.produced.prism.auth.utils.TraceUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal APIs for API key management invoked by core-service.
 * <p>
 * All endpoints are under {@code /internal/**} and are protected by the service-signature filter.
 * </p>
 */
@Tag(name = "内部接口-API Key", description = "仅供服务间调用（core-service），前端勿用")
@RestController
@RequestMapping("/internal/account/api-keys")
@RequiredArgsConstructor
public class InternalApiKeyController {

    private final InternalApiKeyService internalApiKeyService;

    @GetMapping
    @Operation(
        summary = "列出用户 API Key",
        description = """
            供 core-service 在“开发者/API Key”页面展示当前用户已创建的 API Key 列表。

            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<List<InternalApiKeyView>>`；
            - 安全提示：`clientSecret` 仅在创建/重置时返回一次，后续列表查询不会返回明文。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功返回 API Key 列表",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = InternalApiKeyView.class))))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<List<InternalApiKeyView>>> listApiKeys(
        @Parameter(description = "用户ID（auth-service 内部 UUID）") @RequestParam("userId") UUID userId) {
        List<InternalApiKeyView> keys = internalApiKeyService.listUserApiKeys(userId);
        return ResponseEntity.ok(ApiResponse.success(keys).withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping
    @Operation(
        summary = "创建 API Key",
        description = """
            为指定用户创建一个新的 OAuth2 Client（API Key）。

            - `clientSecret` 仅在本次创建成功后返回一次，请立即保存；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<InternalApiKeyView>`。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功创建 API Key（包含 clientSecret）",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalApiKeyView.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数不合法或业务校验失败")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<InternalApiKeyView>> createApiKey(@RequestParam("userId") UUID userId,
                                                                        @RequestBody InternalApiKeyCreateRequest request) {
        try {
            InternalApiKeyView created = internalApiKeyService.createApiKey(userId, request);
            return ResponseEntity.ok(ApiResponse.success(created).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalApiKeyView>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }

    @PostMapping("/{id}/regenerate")
    @Operation(
        summary = "重置 API Key Secret",
        description = """
            重置指定 API Key 的 `clientSecret`（旧 secret 立即失效）。

            - `clientSecret` 仅在本次重置成功后返回一次，请立即保存；
            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<InternalApiKeyView>`。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        description = "成功重置 Secret（包含新的 clientSecret）",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = InternalApiKeyView.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数不合法或业务校验失败")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<InternalApiKeyView>> regenerateSecret(@RequestParam("userId") UUID userId,
                                                                            @PathVariable("id") String apiKeyId) {
        try {
            InternalApiKeyView updated = internalApiKeyService.regenerateSecret(userId, apiKeyId);
            return ResponseEntity.ok(ApiResponse.success(updated).withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<InternalApiKeyView>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }

    @PostMapping("/{id}/revoke")
    @Operation(
        summary = "撤销 API Key",
        description = """
            撤销指定 API Key（对应的 OAuth2 Client 将不可用）。

            - 鉴权：service-signature + IP 白名单；
            - 响应体：`ApiResponse<Object>`（成功时 data 为 null）。
            """)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "成功撤销 API Key")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数不合法或业务校验失败")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "签名无效或无权限（service-signature）")
    public ResponseEntity<ApiResponse<Object>> revokeApiKey(@RequestParam("userId") UUID userId,
                                                            @PathVariable("id") String apiKeyId) {
        try {
            internalApiKeyService.revokeApiKey(userId, apiKeyId);
            return ResponseEntity.ok(ApiResponse.success().withMeta(TraceUtils.getTraceId(), null));
        } catch (BizException ex) {
            return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.<Object>error(ex.getErrorCode(), ex.getMessage())
                    .withMeta(TraceUtils.getTraceId(), null));
        }
    }
}
