package nan.produced.prism.core.integration.auth.client;

import java.util.List;
import java.util.UUID;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.dto.AuthChangePasswordRequest;
import nan.produced.prism.core.integration.auth.dto.AuthRememberedDeviceView;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", configuration = ServiceSignatureFeignConfig.class, path = "/internal/account/security")
public interface AuthAccountSecurityInternalClient {

    @GetMapping("/remember-me/tokens")
    ApiResponse<List<AuthRememberedDeviceView>> listRememberMeTokens(@RequestParam("userId") UUID userId,
                                                                     @RequestParam(value = "activeSeries", required = false) String activeSeries);

    @PostMapping("/remember-me/tokens/{series}/revoke")
    ApiResponse<Object> revokeRememberMeToken(@RequestParam("userId") UUID userId, @PathVariable("series") String series);

    @PostMapping("/remember-me/tokens/revoke-all")
    ApiResponse<Object> revokeAllRememberMeTokens(@RequestParam("userId") UUID userId);

    @PostMapping("/password/change")
    ApiResponse<Object> changePassword(@RequestParam("userId") UUID userId, @RequestBody AuthChangePasswordRequest request);
}
