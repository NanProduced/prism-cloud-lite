package nan.produced.prism.core.integration.auth.client;

import java.util.UUID;
import java.util.List;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.auth.dto.AuthRedeemCodeBatchCreateRequest;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionHistoryPageView;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionRedeemRequest;
import nan.produced.prism.core.integration.auth.dto.AuthSubscriptionView;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "auth-service", contextId = "auth-subscription", configuration = ServiceSignatureFeignConfig.class, path = "/auth/internal/subscription")
public interface AuthSubscriptionInternalClient {

    @GetMapping
    ApiResponse<AuthSubscriptionView> getCurrent(@RequestParam("userId") UUID userId);

    @PostMapping("/redeem")
    ApiResponse<AuthSubscriptionView> redeem(@RequestParam("userId") UUID userId, @RequestBody AuthSubscriptionRedeemRequest request);

    @GetMapping("/events")
    ApiResponse<AuthSubscriptionHistoryPageView> listEvents(@RequestParam("userId") UUID userId,
                                                            @RequestParam("page") int page,
                                                            @RequestParam("size") int size);

    @PostMapping("/redeem-codes/batch")
    ApiResponse<List<String>> createRedeemCodeBatch(@RequestBody AuthRedeemCodeBatchCreateRequest request);
}
