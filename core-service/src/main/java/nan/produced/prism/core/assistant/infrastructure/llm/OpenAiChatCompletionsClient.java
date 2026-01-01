package nan.produced.prism.core.assistant.infrastructure.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.List;

public class OpenAiChatCompletionsClient {

    private final RestClient restClient;
    private final String model;
    private final String apiKey;
    private final double temperature;

    public OpenAiChatCompletionsClient(RestClient restClient, String model, String apiKey, double temperature) {
        this.restClient = restClient;
        this.model = model;
        this.apiKey = apiKey;
        this.temperature = temperature;
    }

    public ChatResult complete(List<Message> messages) {
        ChatCompletionsRequest request = new ChatCompletionsRequest(model, messages, temperature, false);

        RestClient.RequestHeadersSpec<?> spec = restClient.post()
                .uri("/v1/chat/completions")
                .body(request);

        if (StringUtils.hasText(apiKey)) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }

        ChatCompletionsResponse response = spec.retrieve().body(ChatCompletionsResponse.class);
        if (response == null || response.choices == null || response.choices.isEmpty()) {
            throw new IllegalStateException("Empty chat completions response");
        }

        Choice choice = response.choices.get(0);
        if (choice == null || choice.message == null) {
            throw new IllegalStateException("Chat completion missing message");
        }
        return new ChatResult(choice.message.content, choice.finishReason);
    }

    public record Message(String role, String content) {
    }

    public record ChatResult(String content, String finishReason) {
    }

    public record ChatCompletionsRequest(
            String model,
            List<Message> messages,
            double temperature,
            boolean stream
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChatCompletionsResponse {
        public List<Choice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        public MessagePayload message;
        @JsonProperty("finish_reason")
        public String finishReason;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MessagePayload {
        public String role;
        public String content;
    }
}

