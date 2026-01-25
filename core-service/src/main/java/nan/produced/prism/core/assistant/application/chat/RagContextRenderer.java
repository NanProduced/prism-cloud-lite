package nan.produced.prism.core.assistant.application.chat;

import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantRagParentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagContextRenderer {

    public AssistantRagContextService.RagContext render(List<AssistantRagParentRepository.ParentDoc> parents, int maxContextChars) {
        if (parents == null || parents.isEmpty()) {
            return AssistantRagContextService.RagContext.empty();
        }

        Map<String, AssistantRagContextService.RagSource> sourcesBySlug = new LinkedHashMap<>();
        StringBuilder context = new StringBuilder(Math.min(maxContextChars, 16_000));

        for (AssistantRagParentRepository.ParentDoc parent : parents) {
            if (parent == null || !StringUtils.hasText(parent.parentText())) {
                continue;
            }

            sourcesBySlug.putIfAbsent(parent.slug(), new AssistantRagContextService.RagSource(
                    parent.docKey() + "/" + parent.lang(),
                    parent.slug(),
                    parent.title()
            ));

            if (context.length() >= maxContextChars) {
                break;
            }

            context.append("\n---\n");
            context.append("Source: ").append(parent.title()).append(" (").append(parent.lang()).append(") ").append(parent.slug()).append("\n");
            if (StringUtils.hasText(parent.headingPath())) {
                context.append("Heading: ").append(parent.headingPath()).append("\n");
            }
            context.append(parent.parentText()).append("\n");
        }

        String text = context.toString().trim();
        if (text.length() > maxContextChars) {
            text = text.substring(0, maxContextChars);
        }
        return new AssistantRagContextService.RagContext(text, List.copyOf(sourcesBySlug.values()));
    }
}
