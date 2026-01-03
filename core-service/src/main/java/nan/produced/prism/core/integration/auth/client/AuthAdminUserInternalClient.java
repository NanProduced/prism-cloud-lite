package nan.produced.prism.core.integration.auth.client;

import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserCreateRequest;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserCreatedView;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserItem;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserPageView;
import nan.produced.prism.core.integration.auth.dto.AuthAdminUserPasswordResetView;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", contextId = "auth-admin-users", configuration = ServiceSignatureFeignConfig.class, path = "/auth/internal/admin-users")
public interface AuthAdminUserInternalClient {

    @GetMapping
    ApiResponse<AuthAdminUserPageView> list(@RequestParam("type") String type,
                                           @RequestParam("page") int page,
                                           @RequestParam("size") int size);

    @GetMapping("/{publicId}")
    ApiResponse<AuthAdminUserItem> get(@PathVariable("publicId") String publicId);

    @PostMapping
    ApiResponse<AuthAdminUserCreatedView> create(@RequestBody AuthAdminUserCreateRequest request);

    @PostMapping("/{publicId}/lock")
    ApiResponse<Object> lock(@PathVariable("publicId") String publicId);

    @PostMapping("/{publicId}/unlock")
    ApiResponse<Object> unlock(@PathVariable("publicId") String publicId);

    @PostMapping("/{publicId}/password/reset")
    ApiResponse<AuthAdminUserPasswordResetView> resetPassword(@PathVariable("publicId") String publicId);
}

