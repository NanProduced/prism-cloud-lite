package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssistantChatPromptContextService {

    private final AssistantChatProperties properties;
    private final AssistantRagContextService ragContextService;
    private final PromptBuilder promptBuilder;

    public record PromptContext(AssistantRagContextService.RagContext rag, List<AssistantChatMessage> messages) {
    }

    public PromptContext build(String userText,
                               AssistantChatTierLimits limits,
                               Object toolResultForPrompt,
                               List<AiSdkChatRequestParser.ChatMessage> conversation) {
        AssistantRagContextService.RagContext rag = limits.ragEnabled() && properties.rag().enabled()
                ? ragContextService.buildContext(userText, limits.ragTopK(), limits.ragMaxContextChars(), properties.rag().preferLang())
                : AssistantRagContextService.RagContext.empty();

        List<AssistantChatMessage> messages = new ArrayList<>();
        messages.add(new AssistantChatMessage("system", promptBuilder.buildSystemPrompt(rag, toolResultForPrompt)));
        messages.addAll(promptBuilder.toLlmMessages(conversation));

        return new PromptContext(rag, messages);
    }
}
