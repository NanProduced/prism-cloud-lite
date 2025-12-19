package nan.produced.prism.core.integration.auth.client;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.dto.AuthApiKeyCreateRequest;
import nan.produced.prism.core.integration.auth.dto.AuthApiKeyView;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", configuration = ServiceSignatureFeignConfig.class, path = "/internal/account/api-keys")
public interface AuthApiKeyInternalClient {

    @GetMapping
    ApiResponse<List<AuthApiKeyView>> listApiKeys(@RequestParam("userId") UUID userId);

    @PostMapping
    ApiResponse<AuthApiKeyView> createApiKey(@RequestParam("userId") UUID userId,
                                             @RequestBody AuthApiKeyCreateRequest request);

    @PostMapping("/{id}/regenerate")
    ApiResponse<AuthApiKeyView> regenerateSecret(@RequestParam("userId") UUID userId,
                                                 @PathVariable("id") String apiKeyId);

    @PostMapping("/{id}/revoke")
    ApiResponse<Object> revokeApiKey(@RequestParam("userId") UUID userId,
                                     @PathVariable("id") String apiKeyId);
}

