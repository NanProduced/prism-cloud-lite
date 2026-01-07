package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.api.datastream.AiDataStreamWriter;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatProperties;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatTierLimitsProperties;
import nan.produced.prism.core.assistant.infrastructure.config.AssistantChatTokenBudgetProperties;
import nan.produced.prism.core.assistant.infrastructure.llm.AssistantChatLlmClient;
import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient;
import nan.produced.prism.core.assistant.infrastructure.llm.OpenAiChatCompletionsClient.Message;
import nan.produced.prism.core.assistant.application.tools.AssistantToolCall;
import nan.produced.prism.core.assistant.application.tools.AssistantToolExecutor;
import nan.produced.prism.core.assistant.infrastructure.persistence.AssistantChatTokenUsageRepository;
import nan.produced.prism.core.assistant.infrastructure.springai.AssistantChatModelRouter;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatService {

    private final ObjectMapper objectMapper;
    private final AssistantChatProperties properties;
    private final AssistantChatTierLimitsProperties tierLimits;
    private final AssistantChatTokenBudgetProperties tokenBudget;
    private final AssistantChatTokenUsageRepository tokenUsageRepository;
    private final AssistantSystemPromptTemplate systemPromptTemplate;
    private final AssistantNavigationToolResolver navigationToolResolver;
    private final AssistantRagContextService ragContextService;
    private final AssistantChatLlmClient llmClient;
    private final AssistantChatModelRouter modelRouter;
    private final AssistantToolPlanner toolPlanner;
    private final AssistantToolExecutor toolExecutor;
    private final AssistantSelectionTokenParser selectionTokenParser;
    private final AssistantCommandLogPickerResolver commandLogPickerResolver;
    private final AssistantDevicePickerResolver devicePickerResolver;
    private final AssistantPendingToolCallStore pendingToolCallStore;

    public void handle(UUID userId, String tier, JsonNode request, OutputStream outputStream) {
        doHandle(userId, tier, request, new AiDataStreamWriter(objectMapper, outputStream));
    }

    private void doHandle(UUID userId, String tier, JsonNode request, AiDataStreamWriter writer) {
        AssistantChatTierLimits limits = resolveLimits(userId, tier);
        QuotaSnapshot quota = null;

        List<AiSdkChatRequestParser.ChatMessage> conversation = trimConversation(
                AiSdkChatRequestParser.parseUserAndAssistantMessages(request),
                limits.historyMaxMessages(),
                limits.historyMaxChars()
        );
        String rawUserText = lastUserText(conversation);

        AssistantSelectionTokenParser.ParseResult selection = selectionTokenParser.parse(rawUserText);
        conversation = sanitizeUserMessages(conversation);
        String userText = lastUserText(conversation);
        userText = StringUtils.hasText(userText) ? userText : null;

        // Tool result message (AI SDK 5.0): {"role":"tool","tool_call_id":"...","content":"{...}"}
        AiSdkChatRequestParser.ToolMessage toolMessage = AiSdkChatRequestParser.lastToolMessage(request);
        String lastRole = AiSdkChatRequestParser.lastNonSystemRole(request);
        AssistantPendingToolCallStore.PendingToolCall pending = pendingToolCallStore.get(userId);

        ToolSelection selectionFromTool = null;
        if (toolMessage != null && pending != null && pending.toolCallId() != null
                && pending.toolCallId().equals(toolMessage.toolCallId())) {
            selectionFromTool = parseToolSelectionJson(toolMessage.content());
            if (selectionFromTool == null) {
                sendInvalidToolResult(writer);
                return;
            }
            pendingToolCallStore.clear(userId);
        } else if (toolMessage == null && pending != null && "user".equals(lastRole)) {
            // User skipped tool selection and typed a new message; clear pending state and continue.
            pendingToolCallStore.clear(userId);
        }

        if (selectionFromTool != null && selectionFromTool.hasAnySelection()) {
            selection = selectionFromTool.toTokenLikeSelection(selection.cleanedText());
        }

        if (!StringUtils.hasText(userText)) {
            userText = StringUtils.hasText(selection.cleanedText()) ? selection.cleanedText() : rawUserText;
        }
        if (StringUtils.hasText(selection.cleanedText())) {
            userText = selection.cleanedText();
        }
        if (!StringUtils.hasText(userText)) {
            writer.error("Missing user message");
            writer.finish(Map.of("finishReason", "error"));
            return;
        }

        try {
            Object toolResultForPrompt = null;
            if (selection.hasAnySelection()) {
                AssistantToolCall injectedToolCall = selection.toInjectedToolCall(objectMapper);
                if (injectedToolCall != null) {
                    writer.toolCall(injectedToolCall.toolCallId(), injectedToolCall.toolName(), injectedToolCall.input());
                    var exec = toolExecutor.execute(userId, injectedToolCall);
                    if (exec.success()) {
                        toolResultForPrompt = truncateJson(exec.output(), limits.toolResultMaxChars());
                        writer.toolResult(injectedToolCall.toolCallId(), exec.output());
                    } else {
                        writer.toolResult(injectedToolCall.toolCallId(), Map.of("error", exec.errorText()));
                    }
                }
            }

            AssistantNavigationToolResolver.NavigationTarget nav = navigationToolResolver.resolve(userText);
            if (nav != null) {
                String toolCallId = "tool-" + UUID.randomUUID();
                writer.toolCall(toolCallId, "navigateToPage", Map.of("path", nav.path(), "label", nav.label()));
                writer.finish(Map.of("finishReason", "stop"));
                return;
            }

            if (!selection.hasAnySelection()) {
                AssistantCommandLogPickerResolver.PickPayload commandPick = commandLogPickerResolver.resolve(userId, userText);
                if (commandPick != null) {
                    String toolCallId = "tool-" + UUID.randomUUID();
                    pendingToolCallStore.set(userId, toolCallId, "pickCommandLog");
                    writer.toolCall(toolCallId, "pickCommandLog", commandPick);
                    writer.finish(Map.of("finishReason", "tool-calls"));
                    return;
                }

                AssistantDevicePickerResolver.PickPayload devicePick = devicePickerResolver.resolve(userId, userText);
                if (devicePick != null) {
                    String toolCallId = "tool-" + UUID.randomUUID();
                    pendingToolCallStore.set(userId, toolCallId, "pickDevice");
                    writer.toolCall(toolCallId, "pickDevice", devicePick);
                    writer.finish(Map.of("finishReason", "tool-calls"));
                    return;
                }
            }

            AssistantToolCall plannedToolCall = "spring-ai".equalsIgnoreCase(properties.engine())
                    ? null
                    : toolPlanner.plan(userText);
            if (toolResultForPrompt == null && !"spring-ai".equalsIgnoreCase(properties.engine()) && plannedToolCall != null) {
                writer.toolCall(plannedToolCall.toolCallId(), plannedToolCall.toolName(), plannedToolCall.input());
                var exec = toolExecutor.execute(userId, plannedToolCall);
                if (exec.success()) {
                    toolResultForPrompt = truncateJson(exec.output(), limits.toolResultMaxChars());
                    writer.toolResult(plannedToolCall.toolCallId(), exec.output());
                } else {
                    writer.toolResult(plannedToolCall.toolCallId(), Map.of("error", exec.errorText()));
                }
            }

            AssistantRagContextService.RagContext rag = limits.ragEnabled() && properties.rag().enabled()
                    ? ragContextService.buildContext(userText, limits.ragTopK(), limits.ragMaxContextChars(), properties.rag().preferLang())
                    : AssistantRagContextService.RagContext.empty();

            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system", buildSystemPrompt(rag, toolResultForPrompt)));
            messages.addAll(toLlmMessages(conversation));

            // Enforce daily token budgets for local-vllm only.
            Integer maxCompletionTokens = null;
            if (isLocalVllmCall(userId) && tokenBudget != null && tokenBudget.isEnabled()) {
                quota = resolveQuotaSnapshot(userId, tier, messages, tokenBudget.resetZoneId());
                if (quota.blocked()) {
                    sendQuotaExceededAndFinish(writer, quota);
                    return;
                }
                maxCompletionTokens = quota.maxCompletionTokensForThisRequest();
            }

            StringBuilder answerText = quota != null && quota.trackTokens() ? new StringBuilder() : null;
            AssistantChatLlmClient.StreamResult llmResult = llmClient.stream(
                    userId,
                    messages,
                    new AssistantChatLlmClient.StreamOptions(
                            limits.toolMaxRounds(),
                            limits.toolMaxCallsPerRound(),
                            limits.toolResultMaxChars(),
                            maxCompletionTokens != null ? maxCompletionTokens : 0
                    ),
                    new AssistantChatLlmClient.ToolEventListener() {
                        @Override
                        public void onToolInputAvailable(String toolCallId, String toolName, Object input) {
                            writer.toolCall(toolCallId, toolName, input);
                        }

                        @Override
                        public void onToolOutputAvailable(String toolCallId, Object output) {
                            writer.toolResult(toolCallId, output);
                        }

                        @Override
                        public void onToolOutputError(String toolCallId, String errorText) {
                            writer.toolResult(toolCallId, Map.of("error", errorText));
                        }
                    },
                    delta -> {
                        if (answerText != null && delta != null) {
                            answerText.append(delta);
                        }
                        writer.text(delta);
                    }
            );

            for (var source : rag.sources()) {
                writer.data(List.of(Map.of(
                        "type", "source",
                        "source", Map.of(
                                "id", source.sourceId(),
                                "url", source.url(),
                                "title", source.title()
                        )
                )));
            }

            if (quota != null && quota.trackTokens() && llmResult != null) {
                Integer totalTokens = llmResult.totalTokens();
                long deltaTokens = totalTokens != null && totalTokens > 0
                        ? totalTokens
                        : Math.max(1, estimatePromptTokens(messages) + estimateTokensFromText(answerText != null ? answerText.toString() : ""));
                long usedAfter = tokenUsageRepository.addTokens(userId, quota.day(), deltaTokens);
                quota = quota.withUsedTokens(usedAfter);
            }

            String finishReason = llmResult != null ? llmResult.finishReason() : null;
            Map<String, Object> finishChunk = new LinkedHashMap<>();
            finishChunk.put("finishReason", normalizeFinishReason(finishReason));
            if (quota != null && quota.trackTokens()) {
                finishChunk.put("quota", quota.toFrontendPayload());
            }
            writer.finish(finishChunk);
        } catch (Exception e) {
            log.warn("assistant chat failed", e);
            writer.error("Assistant error: " + e.getMessage());
            writer.finish(Map.of("finishReason", "error"));
        }
    }

    private List<AiSdkChatRequestParser.ChatMessage> sanitizeUserMessages(List<AiSdkChatRequestParser.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<AiSdkChatRequestParser.ChatMessage> out = new ArrayList<>(messages.size());
        for (AiSdkChatRequestParser.ChatMessage m : messages) {
            if (m != null && "user".equals(m.role()) && StringUtils.hasText(m.content())) {
                String cleaned = selectionTokenParser.stripKnownTokens(m.content());
                out.add(new AiSdkChatRequestParser.ChatMessage(m.role(), cleaned));
            } else {
                out.add(m);
            }
        }
        return List.copyOf(out);
    }

    private String buildSystemPrompt(AssistantRagContextService.RagContext rag, Object toolResult) {
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

    private AssistantChatTierLimits resolveLimits(UUID userId, String tier) {
        String tierKey = AssistantChatTierLimits.normalizeTierKey(tier);
        if ("spring-ai".equalsIgnoreCase(properties.engine()) && modelRouter != null) {
            try {
                AssistantChatModelRouter.LlmTarget target = modelRouter.resolveForUser(userId);
                boolean byok = target != null
                        && StringUtils.hasText(target.provider())
                        && !"local-vllm".equalsIgnoreCase(target.provider())
                        && StringUtils.hasText(target.apiKey());
                if (byok && AssistantChatTierLimits.TIER_FREE.equalsIgnoreCase(tierKey)) {
                    tierKey = AssistantChatTierLimits.TIER_FREE_BYOK;
                }
            } catch (Exception ignored) {
            }
        }
        return tierLimits.getOrDefault(tierKey);
    }

    private boolean isLocalVllmCall(UUID userId) {
        if (!"spring-ai".equalsIgnoreCase(properties.engine())) {
            return true;
        }
        if (modelRouter == null) {
            return true;
        }
        try {
            AssistantChatModelRouter.LlmTarget target = modelRouter.resolveForUser(userId);
            return target == null || "local-vllm".equalsIgnoreCase(target.provider());
        } catch (Exception e) {
            return true;
        }
    }

    private QuotaSnapshot resolveQuotaSnapshot(UUID userId, String tier, List<Message> messages, ZoneId zoneId) {
        String tierKey = normalizeTierOrFree(tier);
        long dailyLimit = tokenBudget.getDailyLimitOrUnlimited(tierKey);
        if (dailyLimit <= 0) {
            return QuotaSnapshot.unlimited();
        }

        LocalDate day = LocalDate.now(zoneId);
        long used = tokenUsageRepository.getUsedTokens(userId, day);
        long remaining = Math.max(0, dailyLimit - used);
        OffsetDateTime resetAt = day.plusDays(1).atStartOfDay(zoneId).toOffsetDateTime();

        if (remaining <= 0) {
            return QuotaSnapshot.blocked(day, tierKey, dailyLimit, used, resetAt);
        }

        int configuredMaxCompletion = tokenBudget.getMaxCompletionTokensOrDefault(tierKey);
        int estimatedPrompt = estimatePromptTokens(messages);
        long budgetForCompletion = remaining - estimatedPrompt;
        if (budgetForCompletion <= 0) {
            return QuotaSnapshot.blocked(day, tierKey, dailyLimit, used, resetAt);
        }

        int maxCompletionTokens = (int) Math.min(Integer.MAX_VALUE, budgetForCompletion);
        if (configuredMaxCompletion > 0) {
            maxCompletionTokens = Math.min(maxCompletionTokens, configuredMaxCompletion);
        }

        return QuotaSnapshot.allowed(day, tierKey, dailyLimit, used, resetAt, maxCompletionTokens);
    }

    private static int estimatePromptTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long total = 0;
        for (Message m : messages) {
            if (m == null || m.content() == null) {
                continue;
            }
            total += estimateTokensFromText(m.content());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static long estimateTokensFromText(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        long cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                cjk++;
            }
        }
        long nonCjk = text.length() - cjk;
        long approxEnglishTokens = (nonCjk + 3) / 4;
        return cjk + approxEnglishTokens;
    }

    private static String normalizeTierOrFree(String tier) {
        if (!StringUtils.hasText(tier)) {
            return "FREE";
        }
        String upper = tier.trim().toUpperCase(Locale.ROOT);
        return "PRO".equals(upper) ? "PRO" : "FREE";
    }

    private void sendQuotaExceededAndFinish(AiDataStreamWriter writer, QuotaSnapshot quota) {
        if (writer == null || quota == null) {
            return;
        }
        writer.error("今日 AI 助手体验额度已用完（" + quota.tier() + "）。请明天再试，或升级到 Pro，或绑定自己的 API Key。");
        writer.finish(Map.of(
                "finishReason", "error",
                "errorCode", "ASSISTANT_TOKEN_DAILY_LIMIT_EXCEEDED",
                "details", quota.toFrontendPayload()
        ));
    }

    private record QuotaSnapshot(
            boolean trackTokens,
            boolean blocked,
            LocalDate day,
            String tier,
            long dailyLimit,
            long usedTokens,
            OffsetDateTime resetAt,
            Integer maxCompletionTokensForThisRequest
    ) {
        static QuotaSnapshot unlimited() {
            return new QuotaSnapshot(false, false, LocalDate.now(ZoneOffset.UTC), "PRO", 0, 0, OffsetDateTime.now(ZoneOffset.UTC), null);
        }

        static QuotaSnapshot blocked(LocalDate day, String tier, long dailyLimit, long usedTokens, OffsetDateTime resetAt) {
            return new QuotaSnapshot(true, true, day, tier, dailyLimit, usedTokens, resetAt, 0);
        }

        static QuotaSnapshot allowed(LocalDate day, String tier, long dailyLimit, long usedTokens, OffsetDateTime resetAt, int maxCompletionTokens) {
            return new QuotaSnapshot(true, false, day, tier, dailyLimit, usedTokens, resetAt, maxCompletionTokens);
        }

        QuotaSnapshot withUsedTokens(long usedTokens) {
            return new QuotaSnapshot(trackTokens, blocked, day, tier, dailyLimit, usedTokens, resetAt, maxCompletionTokensForThisRequest);
        }

        Map<String, Object> toFrontendPayload() {
            long remaining = dailyLimit > 0 ? Math.max(0, dailyLimit - usedTokens) : -1;
            return Map.of(
                    "tier", tier,
                    "day", day.toString(),
                    "dailyLimit", dailyLimit,
                    "usedTokens", usedTokens,
                    "remainingTokens", remaining,
                    "resetAt", resetAt != null ? resetAt.toString() : null,
                    "provider", "local-vllm"
            );
        }
    }

    private static List<AiSdkChatRequestParser.ChatMessage> trimConversation(List<AiSdkChatRequestParser.ChatMessage> messages,
                                                                             int maxMessages,
                                                                             int maxChars) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int mm = maxMessages <= 0 ? Integer.MAX_VALUE : Math.max(1, maxMessages);
        int mc = maxChars <= 0 ? Integer.MAX_VALUE : Math.max(1, maxChars);

        int lastUserIdx = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if ("user".equals(messages.get(i).role())) {
                lastUserIdx = i;
                break;
            }
        }
        if (lastUserIdx < 0) {
            return List.of();
        }

        int start = Math.max(0, (lastUserIdx + 1) - mm);
        List<AiSdkChatRequestParser.ChatMessage> tail = messages.subList(start, lastUserIdx + 1);

        int total = 0;
        int keepFrom = tail.size() - 1;
        for (int i = tail.size() - 1; i >= 0; i--) {
            AiSdkChatRequestParser.ChatMessage m = tail.get(i);
            int len = m != null && m.content() != null ? m.content().length() : 0;
            if (i != tail.size() - 1 && total + len > mc) {
                keepFrom = i + 1;
                break;
            }
            total += len;
            keepFrom = i;
        }
        return List.copyOf(tail.subList(keepFrom, tail.size()));
    }

    private static String lastUserText(List<AiSdkChatRequestParser.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiSdkChatRequestParser.ChatMessage m = messages.get(i);
            if (m != null && "user".equals(m.role()) && StringUtils.hasText(m.content())) {
                return m.content().trim();
            }
        }
        return null;
    }

    private static List<Message> toLlmMessages(List<AiSdkChatRequestParser.ChatMessage> conversation) {
        if (conversation == null || conversation.isEmpty()) {
            return List.of();
        }
        List<Message> result = new ArrayList<>(conversation.size());
        for (AiSdkChatRequestParser.ChatMessage m : conversation) {
            if (m == null || !StringUtils.hasText(m.role()) || !StringUtils.hasText(m.content())) {
                continue;
            }
            String role = m.role().trim().toLowerCase();
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            result.add(new Message(role, m.content().trim()));
        }
        return result;
    }

    private Object truncateJson(com.fasterxml.jackson.databind.JsonNode node, int maxChars) {
        if (node == null || maxChars <= 0) {
            return node;
        }
        try {
            String json = objectMapper.writeValueAsString(node);
            if (json.length() <= maxChars) {
                return node;
            }
            return Map.of("_truncated", true, "_maxChars", maxChars, "_text", json.substring(0, maxChars));
        } catch (Exception e) {
            return node;
        }
    }

    private void sendInvalidToolResult(AiDataStreamWriter writer) {
        if (writer == null) {
            return;
        }
        writer.error("非法操作或选择已失效");
        writer.finish(Map.of("finishReason", "error"));
    }

    private record ToolSelection(Long deviceId, boolean fleet, Long commandLogId) {
        boolean hasAnySelection() {
            return fleet || deviceId != null || commandLogId != null;
        }

        AssistantSelectionTokenParser.ParseResult toTokenLikeSelection(String cleanedText) {
            return new AssistantSelectionTokenParser.ParseResult(cleanedText, deviceId, fleet, commandLogId);
        }
    }

    private ToolSelection parseToolSelectionJson(String rawJsonString) {
        if (!StringUtils.hasText(rawJsonString)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJsonString);
            if (node == null || !node.isObject()) {
                return null;
            }
            boolean fleet = node.has("fleet") && node.get("fleet").asBoolean(false);
            Long deviceId = parseLongNode(node.get("deviceId"));
            Long commandLogId = parseLongNode(node.get("commandLogId"));
            ToolSelection selection = new ToolSelection(deviceId, fleet, commandLogId);
            return selection.hasAnySelection() ? selection : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Long parseLongNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            long v = node.asLong();
            return v > 0 ? v : null;
        }
        if (node.isTextual()) {
            String s = node.asText();
            if (!StringUtils.hasText(s)) {
                return null;
            }
            try {
                long v = Long.parseLong(s.trim());
                return v > 0 ? v : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String normalizeFinishReason(String raw) {
        if (raw == null || raw.isBlank()) {
            return "stop";
        }
        String value = raw.trim();
        return switch (value) {
            case "stop", "length", "content-filter", "tool-calls", "error", "other" -> value;
            case "tool_calls" -> "tool-calls";
            default -> "other";
        };
    }
}
