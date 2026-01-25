package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatMessage;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatFacade {

    private final ObjectMapper objectMapper;
    private final AssistantChatPromptContextService promptContextService;
    private final ConversationService conversationService;
    private final ToolCallService toolCallService;
    private final AssistantChatRuntimePolicyService runtimePolicyService;
    private final AssistantChatTokenBudgetService tokenBudgetService;
    private final AssistantChatStreamService streamService;

    public void handle(UUID userId, String tier, JsonNode request, OutputStream outputStream) {
        AiUiMessageSseWriter writer = new AiUiMessageSseWriter(objectMapper, outputStream);
        writer.startStep();
        doHandle(userId, tier, request, writer);
    }

    /**
     * <p>
     *     <li>把前端的 UIMessage 请求解析成可控的对话输入（裁剪历史 + 清洗 tokens + 处理 tool output 回传）。</li>
     *     <li>决定是否触发工具/导航/交互选择器（必要时提前结束本轮并等待下一次请求）。</li>
     *     <li>拼装系统 Prompt + RAG 上下文，并调用执行引擎（ChatModel）。</li>
     *     <li>把 LLM 输出、tool 事件、sources、quota 信息以 UI SDK 6 能消费的 SSE JSON 事件流写回前端。</li>
     *     <li>在 local-vllm 场景下做 token 预算治理（每日额度、单次 max completion tokens、落库统计）。</li>
     * </p>
     *
     * @param userId 用户 ID
     * @param tier 订阅等级
     * @param request 请求
     * @param writer SSE writer
     */
    private void doHandle(UUID userId, String tier, JsonNode request, AiUiMessageSseWriter writer) {
        // 获取用户订阅限制
        AssistantChatTierLimits limits = runtimePolicyService.resolveLimits(userId, tier);
        AssistantChatTokenBudgetService.QuotaSnapshot quota = null;

        ConversationService.ConversationResolution resolution = resolveConversation(userId, limits, request, writer);
        if (resolution == null) {
            return;
        }

        List<AiSdkChatRequestParser.ChatMessage> conversation = resolution.conversation();
        AssistantSelectionTokenParser.ParseResult selection = resolution.selection();
        String userText = resolution.userText();

        try {
            ToolCallService.ToolCallOutcome toolOutcome = resolveToolOutcome(
                    userId,
                    userText,
                    conversation,
                    selection,
                    limits,
                    writer
            );
            if (toolOutcome.finished()) {
                return;
            }
            Object toolResultForPrompt = toolOutcome.toolResultForPrompt();

            // 构建 RAG 上下文与系统 Prompt
            AssistantChatPromptContextService.PromptContext promptContext =
                    promptContextService.build(userText, limits, toolResultForPrompt, conversation);
            AssistantRagContextService.RagContext rag = promptContext.rag();
            List<AssistantChatMessage> messages = promptContext.messages();

            // Enforce daily token budgets for local-vllm only.
            Integer maxCompletionTokens = null;
            if (shouldEnforceTokenBudget(userId)) {
                quota = tokenBudgetService.resolveQuotaSnapshot(userId, tier, messages, tokenBudgetService.resetZoneId());
                if (quota.blocked()) {
                    tokenBudgetService.sendQuotaExceededAndFinish(writer, quota);
                    return;
                }
                maxCompletionTokens = quota.maxCompletionTokensForThisRequest();
            }

            streamService.streamAndRespond(
                    userId,
                    messages,
                    maxCompletionTokens,
                    rag,
                    quota,
                    writer
            );
        } catch (Exception e) {
            log.warn("assistant chat failed", e);
            writer.error("Assistant error: " + e.getMessage());
            writer.finish("error", null);
        }
    }

    private ConversationService.ConversationResolution resolveConversation(
            UUID userId,
            AssistantChatTierLimits limits,
            JsonNode request,
            AiUiMessageSseWriter writer
    ) {
        ConversationService.ConversationResolution resolution = conversationService.resolve(userId, limits, request);
        if (!resolution.hasError()) {
            return resolution;
        }
        writer.error(resolution.errorText());
        writer.finish("error", null);
        return null;
    }

    private ToolCallService.ToolCallOutcome resolveToolOutcome(
            UUID userId,
            String userText,
            List<AiSdkChatRequestParser.ChatMessage> conversation,
            AssistantSelectionTokenParser.ParseResult selection,
            AssistantChatTierLimits limits,
            AiUiMessageSseWriter writer
    ) {
        return toolCallService.handle(userId, userText, conversation, selection, limits, writer);
    }

    private boolean shouldEnforceTokenBudget(UUID userId) {
        return runtimePolicyService.isLocalVllmCall(userId) && tokenBudgetService.isEnabled();
    }
}
