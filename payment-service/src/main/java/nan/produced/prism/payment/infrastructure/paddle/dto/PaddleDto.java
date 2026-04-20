package nan.produced.prism.payment.infrastructure.paddle;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Data;

public class PaddleDto {

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreateTransactionRequest {
        private List<TransactionItem> items;
        private Customer customer;
        private Address address;
        @JsonProperty("custom_data")
        private Map<String, Object> customData;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionItem {
        private String priceId;
        private Integer quantity;

        public TransactionItem(String priceId, Integer quantity) {
            this.priceId = priceId;
            this.quantity = quantity;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Customer {
        private String email;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        @JsonProperty("country_code")
        private String countryCode;
        @JsonProperty("postal_code")
        private String postalCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaddleResponse<T> {
        private T data;
        private PaddleMeta meta;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaddleMeta {
        @JsonProperty("request_id")
        private String requestId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transaction {
        private String id;
        private String status;
        @JsonProperty("customer_id")
        private String customerId;
        @JsonProperty("subscription_id")
        private String subscriptionId;
        @JsonProperty("invoice_number")
        private String invoiceNumber;
        private TransactionDetails details;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("updated_at")
        private Instant updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionDetails {
        private PaddleMoney totals;
        @JsonProperty("payment_attempt")
        private PaymentAttempt paymentAttempt;
        private List<TransactionLineItem> lineItems;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaddleMoney {
        private String total;
        private String subtotal;
        private String tax;
        private String currency;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentAttempt {
        private String status;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("error_code")
        private String errorCode;
        @JsonProperty("error_message")
        private String errorMessage;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionLineItem {
        private String id;
        @JsonProperty("price_id")
        private String priceId;
        private Integer quantity;
        private PaddleMoney totals;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subscription {
        private String id;
        private String status;
        @JsonProperty("customer_id")
        private String customerId;
        @JsonProperty("price_id")
        private String priceId;
        @JsonProperty("next_billed_at")
        private Instant nextBilledAt;
        @JsonProperty("paused_at")
        private Instant pausedAt;
        @JsonProperty("canceled_at")
        private Instant canceledAt;
        @JsonProperty("scheduled_change")
        private ScheduledChange scheduledChange;
        @JsonProperty("current_billing_period")
        private BillingPeriod currentBillingPeriod;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("updated_at")
        private Instant updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScheduledChange {
        private String action;
        @JsonProperty("effective_at")
        private Instant effectiveAt;
        @JsonProperty("resume_at")
        private Instant resumeAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BillingPeriod {
        private Instant startsAt;
        private Instant endsAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookEvent {
        @JsonProperty("event_id")
        private String eventId;
        @JsonProperty("event_type")
        private String eventType;
        @JsonProperty("occurred_at")
        private Instant occurredAt;
        @JsonProperty("notification_id")
        private String notificationId;
        private Object data;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebhookPayload {
        private List<WebhookEvent> data;
        private PaddleMeta meta;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubscriptionEventData {
        private String id;
        private String status;
        @JsonProperty("customer_id")
        private String customerId;
        @JsonProperty("price_id")
        private String priceId;
        @JsonProperty("next_billed_at")
        private Instant nextBilledAt;
        @JsonProperty("paused_at")
        private Instant pausedAt;
        @JsonProperty("canceled_at")
        private Instant canceledAt;
        @JsonProperty("scheduled_change")
        private ScheduledChange scheduledChange;
        @JsonProperty("current_billing_period")
        private BillingPeriod currentBillingPeriod;
        @JsonProperty("custom_data")
        private Map<String, Object> customData;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("updated_at")
        private Instant updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionEventData {
        private String id;
        private String status;
        @JsonProperty("customer_id")
        private String customerId;
        @JsonProperty("subscription_id")
        private String subscriptionId;
        @JsonProperty("invoice_number")
        private String invoiceNumber;
        private TransactionDetails details;
        @JsonProperty("custom_data")
        private Map<String, Object> customData;
        @JsonProperty("created_at")
        private Instant createdAt;
        @JsonProperty("updated_at")
        private Instant updatedAt;
    }
}
