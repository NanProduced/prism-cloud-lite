package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptBuilder {

    private final AssistantSystemPromptTemplate systemPromptTemplate;

    public String buildSystemPrompt(AssistantRagContextService.RagContext rag, Object toolResult) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append(systemPromptTemplate.base()).append("\n");
        if (toolResult != null) {
            sb.append("\n# Tool result (server-side)\n");
            sb.append(toolResult);
            sb.append("\n");
        }
        if (!rag.contextText().isBlank()) {
            sb.append("\n# Retrieved context (Help Center)\n");
            sb.append(rag.contextText());
        }
        return sb.toString();
    }

    public List<AssistantChatMessage> toLlmMessages(List<AiSdkChatRequestParser.ChatMessage> conversation) {
        if (conversation == null || conversation.isEmpty()) {
            return List.of();
        }
        List<AssistantChatMessage> result = new ArrayList<>(conversation.size());
        for (AiSdkChatRequestParser.ChatMessage m : conversation) {
            if (m == null || !StringUtils.hasText(m.role()) || !StringUtils.hasText(m.content())) {
                continue;
            }
            String role = m.role().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            result.add(new AssistantChatMessage(role, m.content().trim()));
        }
        return result;
    }
}
