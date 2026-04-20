package nan.produced.prism.payment.interface_.feign;

import java.time.Instant;
import java.util.Map;
import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "auth-service")
public interface AuthServiceFeignClient {

    @PostMapping("/auth/internal/subscription/sync-from-payment")
    void syncSubscriptionFromPayment(@RequestBody SubscriptionSyncRequest request);

    @Data
    class SubscriptionSyncRequest {
        private String userId;
        private String externalSubscriptionId;
        private String tier;
        private Instant startAt;
        private Instant endAt;
        private String status;
        private Map<String, Object> metadata;
    }
}
