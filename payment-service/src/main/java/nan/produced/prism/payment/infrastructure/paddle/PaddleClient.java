package nan.produced.prism.payment.infrastructure.paddle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.CreateTransactionRequest;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.Customer;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.PaddleResponse;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.Subscription;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.Transaction;
import nan.produced.prism.payment.infrastructure.paddle.dto.PaddleDto.TransactionItem;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaddleClient {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String PADDLE_SIGNATURE_HEADER = "Paddle-Signature";

    private final PaddleConfig paddleConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    public Transaction createTransaction(String priceId, String customerEmail, String customerName,
                                          Map<String, Object> customData) {
        String url = paddleConfig.getBaseUrl() + "/transactions";

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setItems(List.of(new TransactionItem(priceId, 1)));

        if (customerEmail != null || customerName != null) {
            Customer customer = new Customer();
            customer.setEmail(customerEmail);
            customer.setName(customerName);
            request.setCustomer(customer);
        }

        request.setCustomData(customData);

        HttpHeaders headers = createHeaders();
        HttpEntity<CreateTransactionRequest> entity = new HttpEntity<>(request, headers);

        log.debug("Creating Paddle transaction: priceId={}, customerEmail={}", priceId, customerEmail);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        try {
            PaddleResponse<Transaction> paddleResponse = objectMapper.readValue(
                response.getBody(),
                objectMapper.getTypeFactory().constructParametricType(PaddleResponse.class, Transaction.class)
            );
            return paddleResponse.getData();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Paddle transaction response", e);
            throw new RuntimeException("Failed to parse Paddle response", e);
        }
    }

    public Transaction getTransaction(String transactionId) {
        String url = paddleConfig.getBaseUrl() + "/transactions/" + transactionId;

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        try {
            PaddleResponse<Transaction> paddleResponse = objectMapper.readValue(
                response.getBody(),
                objectMapper.getTypeFactory().constructParametricType(PaddleResponse.class, Transaction.class)
            );
            return paddleResponse.getData();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Paddle transaction response", e);
            throw new RuntimeException("Failed to parse Paddle response", e);
        }
    }

    public Subscription getSubscription(String subscriptionId) {
        String url = paddleConfig.getBaseUrl() + "/subscriptions/" + subscriptionId;

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        try {
            PaddleResponse<Subscription> paddleResponse = objectMapper.readValue(
                response.getBody(),
                objectMapper.getTypeFactory().constructParametricType(PaddleResponse.class, Subscription.class)
            );
            return paddleResponse.getData();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Paddle subscription response", e);
            throw new RuntimeException("Failed to parse Paddle response", e);
        }
    }

    public Subscription cancelSubscription(String subscriptionId, boolean effectiveImmediately) {
        String url = paddleConfig.getBaseUrl() + "/subscriptions/" + subscriptionId + "/cancel";

        Map<String, Object> request = Map.of(
            "effective_from", effectiveImmediately ? "immediately" : "next_billing_period"
        );

        HttpHeaders headers = createHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        try {
            PaddleResponse<Subscription> paddleResponse = objectMapper.readValue(
                response.getBody(),
                objectMapper.getTypeFactory().constructParametricType(PaddleResponse.class, Subscription.class)
            );
            return paddleResponse.getData();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Paddle subscription response", e);
            throw new RuntimeException("Failed to parse Paddle response", e);
        }
    }

    public Subscription pauseSubscription(String subscriptionId) {
        String url = paddleConfig.getBaseUrl() + "/subscriptions/" + subscriptionId + "/pause";

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        try {
            PaddleResponse<Subscription> paddleResponse = objectMapper.readValue(
                response.getBody(),
                objectMapper.getTypeFactory().constructParametricType(PaddleResponse.class, Subscription.class)
            );
            return paddleResponse.getData();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Paddle subscription response", e);
            throw new RuntimeException("Failed to parse Paddle response", e);
        }
    }

    public Subscription resumeSubscription(String subscriptionId) {
        String url = paddleConfig.getBaseUrl() + "/subscriptions/" + subscriptionId + "/resume";

        HttpHeaders headers = createHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        try {
            PaddleResponse<Subscription> paddleResponse = objectMapper.readValue(
                response.getBody(),
                objectMapper.getTypeFactory().constructParametricType(PaddleResponse.class, Subscription.class)
            );
            return paddleResponse.getData();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Paddle subscription response", e);
            throw new RuntimeException("Failed to parse Paddle response", e);
        }
    }

    public boolean verifyWebhookSignature(String signatureHeader, String payload) {
        if (signatureHeader == null || signatureHeader.isEmpty()) {
            log.warn("Missing Paddle-Signature header");
            return false;
        }

        try {
            String[] parts = signatureHeader.split(";");
            String timestamp = null;
            String hmacSignature = null;

            for (String part : parts) {
                String[] keyValue = part.split("=", 2);
                if (keyValue.length == 2) {
                    if ("ts".equals(keyValue[0])) {
                        timestamp = keyValue[1];
                    } else if ("h1".equals(keyValue[0])) {
                        hmacSignature = keyValue[1];
                    }
                }
            }

            if (timestamp == null || hmacSignature == null) {
                log.warn("Invalid Paddle-Signature format: {}", signatureHeader);
                return false;
            }

            String signedPayload = timestamp + ":" + payload;
            String expectedSignature = computeHmacSha256(signedPayload, paddleConfig.getWebhookSecret());

            boolean valid = hmacSignature.equals(expectedSignature);
            if (!valid) {
                log.warn("Webhook signature verification failed");
            }
            return valid;

        } catch (Exception e) {
            log.error("Failed to verify webhook signature", e);
            return false;
        }
    }

    private String computeHmacSha256(String data, String secret)
        throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
            secret.getBytes(StandardCharsets.UTF_8),
            HMAC_SHA256
        );
        mac.init(secretKeySpec);
        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hmacBytes);
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(paddleConfig.getApiKey());
        return headers;
    }
}
