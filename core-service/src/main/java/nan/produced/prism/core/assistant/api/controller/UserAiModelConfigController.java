package nan.produced.prism.core.assistant.api.controller;

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

    @GetMapping
    public BffResponse<List<UserAiModelConfigResp>> list() {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        List<UserAiModelConfigResp> data = service.listForUser(userId).stream()
                .map(v -> new UserAiModelConfigResp(v.provider(), v.model(), v.enabled(), v.isDefault(), v.hasApiKey(), v.apiKeyLast4()))
                .toList();
        return new BffResponse<>(true, data, null, TraceUtils.getTraceId());
    }

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

    @PostMapping("/{provider}/default")
    public BffResponse<Void> setDefault(@PathVariable("provider") String provider) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        service.setDefault(userId, provider);
        return new BffResponse<>(true, null, null, TraceUtils.getTraceId());
    }

    @DeleteMapping("/{provider}")
    public BffResponse<Void> delete(@PathVariable("provider") String provider) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        service.delete(userId, provider);
        return new BffResponse<>(true, null, null, TraceUtils.getTraceId());
    }
}

