package nan.produced.prism.core.integration.auth.client;

import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.dto.AuthInternalUserResponse;
import nan.produced.prism.core.integration.auth.dto.AuthInternalUserSearchPageView;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", contextId = "auth-info", configuration = ServiceSignatureFeignConfig.class, path = "/auth/internal/users")
public interface AuthInternalClient {

    @GetMapping("/{publicId}")
    ApiResponse<AuthInternalUserResponse> getUserByPublicId(@PathVariable("publicId") String publicId);

    @GetMapping("/search")
    ApiResponse<AuthInternalUserSearchPageView> search(@RequestParam("q") String q,
                                                       @RequestParam("page") int page,
                                                       @RequestParam("size") int size);

    @PostMapping("/{userId}/lock")
    ApiResponse<Object> lock(@PathVariable("userId") String userId);

    @PostMapping("/{userId}/unlock")
    ApiResponse<Object> unlock(@PathVariable("userId") String userId);
}
