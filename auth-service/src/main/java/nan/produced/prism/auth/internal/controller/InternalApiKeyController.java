package nan.produced.prism.auth.internal.controller;

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
@RestController
@RequestMapping("/internal/account/api-keys")
@RequiredArgsConstructor
public class InternalApiKeyController {

    private final InternalApiKeyService internalApiKeyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InternalApiKeyView>>> listApiKeys(@RequestParam("userId") UUID userId) {
        List<InternalApiKeyView> keys = internalApiKeyService.listUserApiKeys(userId);
        return ResponseEntity.ok(ApiResponse.success(keys).withMeta(TraceUtils.getTraceId(), null));
    }

    @PostMapping
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

