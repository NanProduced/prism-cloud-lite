package nan.produced.prism.core.assistant.application.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.infrastructure.springai.AssistantChatModelFactory;
import nan.produced.prism.core.assistant.infrastructure.springai.AssistantChatModelRouter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantToolPlanService {

    private final AssistantChatModelRouter modelRouter;
    private final AssistantChatModelFactory modelFactory;
    private final AssistantToolPlanPromptBuilder promptBuilder;
    private final AssistantToolPlanParser planParser;
    private final AssistantToolPlanValidator planValidator;
    private final AssistantToolPlanGate planGate;

    public ToolPlanDecision plan(UUID userId,
                                 AssistantChatTierLimits limits,
                                 List<AiSdkChatRequestParser.ChatMessage> conversation,
                                 String userText) {
        if (!StringUtils.hasText(userText)) {
            return ToolPlanDecision.error("missing_user_text", "User message is required.");
        }

        try {
            AssistantChatModelRouter.LlmTarget target = modelRouter.resolveForUser(userId);
            String apiKey = modelFactory.resolveApiKey(target);
            OpenAiApi api = modelFactory.buildApi(target, apiKey);
            String model = modelFactory.resolveModel(target, null);

            OpenAiChatOptions options = modelFactory.buildChatOptions(
                    target,
                    model,
                    null
            );
            OpenAiChatModel chatModel = modelFactory.buildChatModel(api, options);

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(promptBuilder.buildSystemPrompt()));
            List<org.springframework.ai.chat.messages.Message> history = toSpringAiMessages(conversation);
            messages.addAll(history);
            if (shouldAppendUserText(history)) {
                messages.add(new UserMessage(userText));
            }

            ChatResponse response = chatModel.call(new Prompt(messages, options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return ToolPlanDecision.error("empty_response", "Tool plan response is empty.");
            }

            String text = response.getResult().getOutput().getText();
            AssistantToolPlanParser.ToolPlanParseResult parsed = planParser.parse(text);
            if (!parsed.success()) {
                return ToolPlanDecision.error(parsed.error().code(), parsed.error().message());
            }

            AssistantToolPlan plan = parsed.plan();
            AssistantToolPlanValidator.ToolPlanValidationResult validation = planValidator.validate(plan);
            if (!validation.valid()) {
                return ToolPlanDecision.invalid(validation);
            }

            AssistantToolPlanGate.ToolPlanGateResult gateResult = planGate.evaluate(plan, limits);
            if (!gateResult.allowed()) {
                return ToolPlanDecision.error("tool_plan_blocked", gateResult.reason());
            }

            return ToolPlanDecision.success(plan);
        } catch (Exception e) {
            log.warn("tool plan generation failed", e);
            return ToolPlanDecision.error("tool_plan_failed", e.getMessage() != null ? e.getMessage() : "Tool plan failed");
        }
    }

    private static List<org.springframework.ai.chat.messages.Message> toSpringAiMessages(List<AiSdkChatRequestParser.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<org.springframework.ai.chat.messages.Message> result = new ArrayList<>(messages.size());
        for (AiSdkChatRequestParser.ChatMessage m : messages) {
            if (m == null || !StringUtils.hasText(m.role()) || !StringUtils.hasText(m.content())) {
                continue;
            }
            String role = m.role().trim().toLowerCase();
            String content = m.content().trim();
            if ("user".equals(role)) {
                result.add(new UserMessage(content));
            } else if ("assistant".equals(role)) {
                result.add(new AssistantMessage(content));
            }
        }
        return result;
    }

    private static boolean shouldAppendUserText(List<org.springframework.ai.chat.messages.Message> history) {
        if (history == null || history.isEmpty()) {
            return true;
        }
        org.springframework.ai.chat.messages.Message last = history.get(history.size() - 1);
        return !(last instanceof UserMessage);
    }

    public record ToolPlanDecision(AssistantToolPlan plan,
                                   AssistantToolPlanValidator.ToolPlanValidationResult validation,
                                   String errorCode,
                                   String errorMessage) {
        public static ToolPlanDecision success(AssistantToolPlan plan) {
            return new ToolPlanDecision(plan, null, null, null);
        }

        public static ToolPlanDecision invalid(AssistantToolPlanValidator.ToolPlanValidationResult validation) {
            return new ToolPlanDecision(null, validation, "tool_plan_invalid", "Tool plan failed validation.");
        }

        public static ToolPlanDecision error(String code, String message) {
            return new ToolPlanDecision(null, null, code, message);
        }

        public boolean success() {
            return plan != null && errorCode == null;
        }
    }
}
