package nan.produced.prism.payment.interface_.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.payment.application.service.WebhookProcessingService;
import nan.produced.prism.payment.common.response.BffResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhook接口", description = "Paddle Webhook接收接口")
public class WebhookController {

    private static final String PADDLE_SIGNATURE_HEADER = "Paddle-Signature";

    private final WebhookProcessingService webhookProcessingService;

    @PostMapping("/paddle")
    @Operation(
        summary = "Paddle Webhook接收",
        description = "接收Paddle发送的Webhook事件通知"
    )
    public ResponseEntity<BffResponse<Void>> handlePaddleWebhook(HttpServletRequest request) {
        String signatureHeader = request.getHeader(PADDLE_SIGNATURE_HEADER);
        String payload = readPayload(request);

        if (payload == null || payload.isEmpty()) {
            log.warn("Received empty webhook payload");
            return ResponseEntity.badRequest().build();
        }

        log.info("Received Paddle webhook: signatureHeader={}", signatureHeader);
        log.debug("Webhook payload: {}", payload);

        try {
            webhookProcessingService.processWebhook(signatureHeader, payload);
            return ResponseEntity.ok(BffResponse.success());
        } catch (Exception e) {
            log.error("Failed to process webhook", e);
            return ResponseEntity.status(500).build();
        }
    }

    private String readPayload(HttpServletRequest request) {
        StringBuilder payload = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                payload.append(line);
            }
            return payload.toString();
        } catch (IOException e) {
            log.error("Failed to read webhook payload", e);
            return null;
        }
    }
}
