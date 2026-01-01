package nan.produced.prism.core.assistant.application.chat;

import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AssistantSystemPromptTemplate {

    private final String template;

    public AssistantSystemPromptTemplate(AssistantChatProperties properties, ResourceLoader resourceLoader) {
        String location = properties.prompt() != null ? properties.prompt().systemTemplate() : null;
        if (!StringUtils.hasText(location)) {
            location = "classpath:assistant/prompts/system.md";
        }
        this.template = loadOrFallback(resourceLoader, location);
    }

    public String base() {
        return template;
    }

    private static String loadOrFallback(ResourceLoader resourceLoader, String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                log.warn("assistant system prompt template not found: {}", location);
                return defaultFallback();
            }
            try (var in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log.warn("assistant system prompt template load failed: {}", location, e);
            return defaultFallback();
        }
    }

    private static String defaultFallback() {
        return """
                You are Prism Cloud Lite AI Assistant.
                Answer in Chinese by default unless the user asks in English.
                Prefer server-side tool results and retrieved docs context over assumptions.
                If uncertain, say you are uncertain and ask for needed identifiers.
                """.trim();
    }
}

