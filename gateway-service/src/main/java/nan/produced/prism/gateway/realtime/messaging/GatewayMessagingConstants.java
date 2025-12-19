package nan.produced.prism.gateway.realtime.messaging;

/**
 * Gateway 实时推送相关的 MQ 常量
 */
public final class GatewayMessagingConstants {

    private GatewayMessagingConstants() {
    }

    public static final class Exchanges {

        public static final String CORE_NOTIFICATIONS = "core.notifications";

        private Exchanges() {
        }
    }

    public static final class Queues {

        /**
         * core-service 推送给 SPA 的通知队列（gateway 消费并通过 SSE 下发）
         */
        public static final String COMMON_NOTIFY = "core-notify-q";

        private Queues() {
        }
    }

    public static final class RoutingKeys {

        public static final String NOTIFY_ALL = "notify.*";

        private RoutingKeys() {
        }
    }
}

