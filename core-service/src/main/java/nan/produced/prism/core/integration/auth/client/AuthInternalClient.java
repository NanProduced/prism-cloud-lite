package nan.produced.prism.core.integration.auth.client;

import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.dto.AuthInternalUserResponse;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "auth-service", configuration = ServiceSignatureFeignConfig.class, path = "/internal/users")
public interface AuthInternalClient {

    @GetMapping("/{publicId}")
    ApiResponse<AuthInternalUserResponse> getUserByPublicId(@PathVariable("publicId") String publicId);
}
