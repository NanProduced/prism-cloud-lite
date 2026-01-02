package nan.produced.prism.core.assistant.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.chat.AssistantChatService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssistantChatController {

    private final AssistantChatService assistantChatService;

    @PostMapping(path = "/api/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody JsonNode request) {
        // Let the client keep the connection open; caller controls abort.
        SseEmitter emitter = new SseEmitter(0L);
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        String tier = user.tier();
        assistantChatService.handle(userId, tier, request, emitter);
        return emitter;
    }
}
