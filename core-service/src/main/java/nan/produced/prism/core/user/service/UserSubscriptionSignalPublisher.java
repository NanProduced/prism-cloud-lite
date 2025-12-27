package nan.produced.prism.core.user.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.messaging.FrontendEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.common.messaging.RabbitMessagePublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserSubscriptionSignalPublisher {

    private static final String TYPE_SUBSCRIPTION_UPDATED = "subscription.updated";

    private final RabbitMessagePublisher rabbitMessagePublisher;

    public void publishSubscriptionUpdated(UUID userId, String tier, Object startAt, Object endAt, Boolean proActive) {
        if (userId == null) {
            return;
        }

        try {
            Map<String, Object> data = new HashMap<>();
            if (tier != null) {
                data.put("tier", tier);
            }
            if (startAt != null) {
                data.put("startAt", startAt);
            }
            if (endAt != null) {
                data.put("endAt", endAt);
            }
            if (proActive != null) {
                data.put("proActive", proActive);
            }

            FrontendEventMessage message = FrontendEventMessage.builder()
                    .success(true)
                    .type(TYPE_SUBSCRIPTION_UPDATED)
                    .scope(FrontendEventMessage.Scope.builder()
                            .userId(userId)
                            .build())
                    .data(data)
                    .build();

            rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.NOTIFY_SUBSCRIPTION_UPDATED, message);
        } catch (Exception ex) {
            log.debug("subscription.updated publish failed (ignored): userId={}", userId, ex);
        }
    }
}
