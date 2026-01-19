package nan.produced.prism.core.assistant.application.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.assistant.api.uimessage.AiUiMessageSseWriter;
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
        AiUiMessageSseWriter writer = new AiUiMessageSseWriter(objectMapper, outputStream);
        writer.startStep();
        doHandle(userId, tier, request, writer);
    }

    /**
     * <p>
     *     <li>把前端的 UIMessage 请求解析成可控的对话输入（裁剪历史 + 清洗 tokens + 处理 tool output 回传）。</li>
     *     <li>决定是否触发工具/导航/交互选择器（必要时提前结束本轮并等待下一次请求）。</li>
     *     <li>拼装系统 Prompt + RAG 上下文，并调用 LLM（AssistantChatLlmClient）。</li>
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
        AssistantChatTierLimits limits = resolveLimits(userId, tier);
        QuotaSnapshot quota = null;

        // 会话裁剪
        List<AiSdkChatRequestParser.ChatMessage> conversation = trimConversation(
                AiSdkChatRequestParser.parseUserAndAssistantMessages(request),
                limits.historyMaxMessages(),
                limits.historyMaxChars()
        );
        String rawUserText = lastUserText(conversation);

        // 解析最后一条用户请求中的选择token
        AssistantSelectionTokenParser.ParseResult selection = selectionTokenParser.parse(rawUserText);
        // 清洗用户消息中的Token
        conversation = sanitizeUserMessages(conversation);
        String userText = lastUserText(conversation);
        userText = StringUtils.hasText(userText) ? userText : null;

        String lastRole = AiSdkChatRequestParser.lastNonSystemRole(request);
        // 后端上一轮发了 pickDevice/pickCommandLog，前端用户选完后会通过 addToolOutput(...) 回传，回传内容存在 下一次请求的 messages[].parts[] 里
        AssistantPendingToolCallStore.PendingToolCall pending = pendingToolCallStore.get(userId);

        ToolSelection selectionFromTool = null;
        if (pending != null && pending.toolCallId() != null) {
            // 处理前端回传的tool output
            AiSdkChatRequestParser.ToolOutput toolOutput =
                    AiSdkChatRequestParser.findToolOutput(request, pending.toolCallId(), pending.toolName());
            if (toolOutput != null) {
                // 场景A: 用户成功选择了内容
                if ("output-available".equalsIgnoreCase(toolOutput.state()) && toolOutput.output() != null) {
                    selectionFromTool = parseToolSelectionNode(toolOutput.output());
                    if (selectionFromTool == null) {
                        sendInvalidToolResult(writer);
                        return;
                    }
                    pendingToolCallStore.clear(userId);
                }
                // 场景B: 前端执行出错或用户取消 (Error)
                else if ("output-error".equalsIgnoreCase(toolOutput.state())) {
                    pendingToolCallStore.clear(userId);
                    writer.error(toolOutput.errorText() != null ? toolOutput.errorText() : "工具执行失败或用户取消");
                    writer.finish("error", null);
                    return;
                }
            }
            // 场景C: 用户“无视”了选择器 (Skip/Override)
            else if ("user".equals(lastRole)) {
                // User skipped tool selection and typed a new message; clear pending state and continue.
                pendingToolCallStore.clear(userId);
            }
        } else if (pending != null && "user".equals(lastRole)) {
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
            writer.finish("error", null);
            return;
        }

        try {
            Object toolResultForPrompt = null;
            if (selection.hasAnySelection()) {
                // 尝试注入工具调用
                AssistantToolCall injectedToolCall = selection.toInjectedToolCall(objectMapper);
                if (injectedToolCall != null) {
                    writer.toolInputAvailable(injectedToolCall.toolCallId(), injectedToolCall.toolName(), injectedToolCall.input(), true);
                    var exec = toolExecutor.execute(userId, injectedToolCall);
                    if (exec.success()) {
                        toolResultForPrompt = truncateJson(exec.output(), limits.toolResultMaxChars());
                        writer.toolOutputAvailable(injectedToolCall.toolCallId(), exec.output(), true);
                    } else {
                        writer.toolOutputError(injectedToolCall.toolCallId(), exec.errorText(), true);
                    }
                }
            }

            // 判断是否是“带我去/打开…”类请求
            AssistantNavigationToolResolver.NavigationTarget nav = navigationToolResolver.resolve(userText);
            // 命中后发出 navigateToPage 的 tool input，并直接 finish("stop") 结束本轮
            if (nav != null) {
                String toolCallId = "tool-" + UUID.randomUUID();
                writer.toolInputAvailable(toolCallId, "navigateToPage", Map.of("path", nav.path(), "label", nav.label()));
                writer.finish("stop", null);
                return;
            }

            // 处理“需要用户选择”的交互型工具
            if (!selection.hasAnySelection()) {

                // 如果用户问“设备状态”但没指定具体设备 → 触发 pickDevice 列表；
                AssistantCommandLogPickerResolver.PickPayload commandPick = commandLogPickerResolver.resolve(userId, userText);
                if (commandPick != null) {
                    String toolCallId = "tool-" + UUID.randomUUID();
                    pendingToolCallStore.set(userId, toolCallId, "pickCommandLog");
                    writer.toolInputAvailable(toolCallId, "pickCommandLog", commandPick);
                    writer.finish("tool-calls", null);
                    return;
                }

                // 如果用户问“指令为什么没执行”但没指定具体指令 → 触发 pickCommandLog 列表；
                AssistantDevicePickerResolver.PickPayload devicePick = devicePickerResolver.resolve(userId, userText);
                if (devicePick != null) {
                    String toolCallId = "tool-" + UUID.randomUUID();
                    pendingToolCallStore.set(userId, toolCallId, "pickDevice");
                    writer.toolInputAvailable(toolCallId, "pickDevice", devicePick);
                    writer.finish("tool-calls", null);
                    return;
                }
            }

            // 当 assistant.chat.engine 不是 spring-ai 时，使用 AssistantToolPlanner 的 规则式规划（例如“离线设备”触发 analyzeOfflineDevices）
            AssistantToolCall plannedToolCall = "spring-ai".equalsIgnoreCase(properties.engine())
                    ? null
                    : toolPlanner.plan(userText);
            if (toolResultForPrompt == null && !"spring-ai".equalsIgnoreCase(properties.engine()) && plannedToolCall != null) {
                writer.toolInputAvailable(plannedToolCall.toolCallId(), plannedToolCall.toolName(), plannedToolCall.input(), true);
                var exec = toolExecutor.execute(userId, plannedToolCall);
                // 若命中则执行工具，结果同样进入 toolResultForPrompt。
                if (exec.success()) {
                    toolResultForPrompt = truncateJson(exec.output(), limits.toolResultMaxChars());
                    writer.toolOutputAvailable(plannedToolCall.toolCallId(), exec.output(), true);
                } else {
                    writer.toolOutputError(plannedToolCall.toolCallId(), exec.errorText(), true);
                }
            }

            // 构建 RAG 上下文与系统 Prompt
            AssistantRagContextService.RagContext rag = limits.ragEnabled() && properties.rag().enabled()
                    // 若 RAG 开启，调用 AssistantRagContextService.buildContext 拉取帮助中心片段；
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
            ThinkTagStreamSplitter thinkSplitter = new ThinkTagStreamSplitter(writer);
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
                            writer.toolInputAvailable(toolCallId, toolName, input, true);
                        }

                        @Override
                        public void onToolOutputAvailable(String toolCallId, Object output) {
                            writer.toolOutputAvailable(toolCallId, output, true);
                        }

                        @Override
                        public void onToolOutputError(String toolCallId, String errorText) {
                            writer.toolOutputError(toolCallId, errorText, true);
                        }
                    },
                    delta -> {
                        if (answerText != null && delta != null) {
                            answerText.append(delta);
                        }
                        thinkSplitter.accept(delta);
                    }
            );
            thinkSplitter.flush();

            for (var source : rag.sources()) {
                writer.sourceUrl(source.sourceId(), source.url(), source.title());
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
            String normalizedFinishReason = normalizeFinishReason(finishReason);
            Object messageMetadata = null;
            if (quota != null && quota.trackTokens()) {
                messageMetadata = Map.of("quota", quota.toFrontendPayload());
            }
            writer.finish(normalizedFinishReason, messageMetadata);
        } catch (Exception e) {
            log.warn("assistant chat failed", e);
            writer.error("Assistant error: " + e.getMessage());
            writer.finish("error", null);
        }
    }

    /**
     * Per-request streaming splitter for local-model {@code <think>...</think>} outputs.
     * <p>用于处理本地模型的流式输出分割</p>
     * <p>Implementation detail: stored in a ThreadLocal so it can be referenced from the delta callback without
     * additional closure wiring.</p>
     */
    private static final class ThinkTagStreamSplitter {

        private static final String OPEN = "<think>";
        private static final String CLOSE = "</think>";

        private final AiUiMessageSseWriter writer;
        private final StringBuilder buffer = new StringBuilder(1024);
        private Mode mode = Mode.TEXT;

        private enum Mode {TEXT, THINK}

        ThinkTagStreamSplitter(AiUiMessageSseWriter writer) {
            this.writer = writer;
        }

        void accept(String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            buffer.append(delta);
            process(false);
        }

        void flush() {
            process(true);
        }

        private void process(boolean flushAll) {
            while (true) {
                if (mode == Mode.TEXT) {
                    int openIdx = buffer.indexOf(OPEN);
                    if (openIdx >= 0) {
                        emitText(buffer.substring(0, openIdx));
                        buffer.delete(0, openIdx + OPEN.length());
                        mode = Mode.THINK;
                        continue;
                    }

                    int keep = flushAll ? 0 : keepSuffixThatMayStartTag(buffer, OPEN);
                    if (keep > 0) {
                        int emitLen = buffer.length() - keep;
                        emitText(buffer.substring(0, emitLen));
                        buffer.delete(0, emitLen);
                    } else {
                        emitText(buffer.toString());
                        buffer.setLength(0);
                    }
                    return;
                }

                int closeIdx = buffer.indexOf(CLOSE);
                if (closeIdx >= 0) {
                    emitReasoning(buffer.substring(0, closeIdx));
                    buffer.delete(0, closeIdx + CLOSE.length());
                    mode = Mode.TEXT;
                    continue;
                }

                int keep = flushAll ? 0 : keepSuffixThatMayStartTag(buffer, CLOSE);
                if (keep > 0) {
                    int emitLen = buffer.length() - keep;
                    emitReasoning(buffer.substring(0, emitLen));
                    buffer.delete(0, emitLen);
                } else {
                    emitReasoning(buffer.toString());
                    buffer.setLength(0);
                }
                return;
            }
        }

        private static int keepSuffixThatMayStartTag(CharSequence buf, String tag) {
            int max = Math.min(buf.length(), tag.length() - 1);
            for (int keep = max; keep >= 1; keep--) {
                boolean matches = true;
                for (int j = 0; j < keep; j++) {
                    if (buf.charAt(buf.length() - keep + j) != tag.charAt(j)) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return keep;
                }
            }
            return 0;
        }

        private void emitText(String s) {
            if (s == null || s.isEmpty()) {
                return;
            }
            writer.textDelta(s);
        }

        private void emitReasoning(String s) {
            if (s == null || s.isEmpty()) {
                return;
            }
            writer.reasoningDelta(s);
        }
    }

    /**
     * 会对所有 user 消息调用 stripKnownTokens，把这些 token 从 user 文本中剥离掉
     * @param messages 用户消息
     * @return 修剪后的用户消息
     */
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
        // 基础设定 - 系统提示词
        sb.append(systemPromptTemplate.base()).append("\n");
        // 服务器端工具结果
        if (toolResult != null) {
            sb.append("\n# Tool result (server-side)\n");
            sb.append(toolResult);
            sb.append("\n");
        }
        // RAG 检索知识
        if (!rag.contextText().isBlank()) {
            sb.append("\n# Retrieved context (Help Center)\n");
            sb.append(rag.contextText());
        }
        return sb.toString();
    }

    /**
     * 根据订阅获取默认聊天限制（history/RAG/tools）
     * @param userId 用户ID
     * @param tier 订阅
     * @return 聊天限制
     */
    private AssistantChatTierLimits resolveLimits(UUID userId, String tier) {
        String tierKey = AssistantChatTierLimits.normalizeTierKey(tier);
        if ("spring-ai".equalsIgnoreCase(properties.engine()) && modelRouter != null) {
            try {
                AssistantChatModelRouter.LlmTarget target = modelRouter.resolveForUser(userId);
                boolean byok = target != null
                        && StringUtils.hasText(target.provider())
                        && !"local-vllm".equalsIgnoreCase(target.provider())
                        && StringUtils.hasText(target.apiKey());
                // 如果用户使用 BYOK 模型，则使用 BYOK 模型对应的免费
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

    private void sendQuotaExceededAndFinish(AiUiMessageSseWriter writer, QuotaSnapshot quota) {
        if (writer == null || quota == null) {
            return;
        }
        writer.error("今日 AI 助手体验额度已用完（" + quota.tier() + "）。请明天再试，或升级到 Pro，或绑定自己的 API Key。");
        writer.finish("stop", Map.of(
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

    /**
     * 只保留最后一段到“最后一条 user 消息”为止，并从尾部往前累计字符数，超出上限就丢更早的消息。
     * <P>目的：控制 prompt 大小，避免对话无限增长。</P>
     * @param messages 聊天消息（用户/助手）
     * @param maxMessages 最多消息数
     * @param maxChars 最大字符数
     * @return 截断后的消息列表
     */
    private static List<AiSdkChatRequestParser.ChatMessage> trimConversation(List<AiSdkChatRequestParser.ChatMessage> messages,
                                                                             int maxMessages,
                                                                             int maxChars) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        // 0或-1视为不限制
        int mm = maxMessages <= 0 ? Integer.MAX_VALUE : maxMessages;
        int mc = maxChars <= 0 ? Integer.MAX_VALUE : maxChars;

        // 定位最后一条用户消息
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

        // 以 lastUserIdx 为终点，向前保留最多 mm 条消息
        int start = Math.max(0, (lastUserIdx + 1) - mm);
        List<AiSdkChatRequestParser.ChatMessage> tail = messages.subList(start, lastUserIdx + 1);

        int total = 0;
        int keepFrom = tail.size() - 1;
        for (int i = tail.size() - 1; i >= 0; i--) {
            AiSdkChatRequestParser.ChatMessage m = tail.get(i);
            int len = m != null && m.content() != null ? m.content().length() : 0;
            // 循环条件中 i != tail.size() - 1 说明最后一条消息（当前的提问）即使超过 maxChars 也会被保留（为了保证请求有效）
            // 从倒数第二条开始累加。一旦总长度 total + len 超过了 mc，就停止增加，并确定 keepFrom 的位置
            if (i != tail.size() - 1 && total + len > mc) {
                keepFrom = i + 1;
                break;
            }
            total += len;
            keepFrom = i;
        }
        // 使用 List.copyOf 和 List.of 返回不可变列表，防止外部修改影响内部状态，这在多线程（特别是你使用的虚拟线程）环境下非常安全
        return List.copyOf(tail.subList(keepFrom, tail.size()));
    }

    /**
     * 获取最后一条用户消息的文本
     * @param messages 聊天消息（用户/助手）
     * @return 最后一条用户消息的文本
     */
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

    /**
     * 将前端协议格式的消息（AiSdkChatRequestParser.ChatMessage）转换为大模型客户端（LLM Client）能够识别的标准消息格式（Message）
     * @param conversation 聊天消息（用户/助手）
     * @return 标准消息格式
     */
    private static List<Message> toLlmMessages(List<AiSdkChatRequestParser.ChatMessage> conversation) {
        if (conversation == null || conversation.isEmpty()) {
            return List.of();
        }
        List<Message> result = new ArrayList<>(conversation.size());
        for (AiSdkChatRequestParser.ChatMessage m : conversation) {
            // 如果某条消息没有角色（role）或者没有内容（content），直接跳过
            if (m == null || !StringUtils.hasText(m.role()) || !StringUtils.hasText(m.content())) {
                continue;
            }
            String role = m.role().trim().toLowerCase();
            // 只允许 user 和 assistant 角色通过
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            result.add(new Message(role, m.content().trim()));
        }
        return result;
    }

    /**
     * 截断 Json
     * @param node  Json
     * @param maxChars 最大字符数
     * @return 截断后的 Json
     */
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

    private void sendInvalidToolResult(AiUiMessageSseWriter writer) {
        if (writer == null) {
            return;
        }
        writer.error("非法操作或选择已失效");
        writer.finish("error", null);
    }

    private record ToolSelection(Long deviceId, boolean fleet, Long commandLogId) {
        boolean hasAnySelection() {
            return fleet || deviceId != null || commandLogId != null;
        }

        AssistantSelectionTokenParser.ParseResult toTokenLikeSelection(String cleanedText) {
            return new AssistantSelectionTokenParser.ParseResult(cleanedText, deviceId, fleet, commandLogId);
        }
    }

    /**
     * 解析工具选择
     * @param node 工具选择节点
     * @return 工具选择
     */
    private ToolSelection parseToolSelectionNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        boolean fleet = node.has("fleet") && node.get("fleet").asBoolean(false);
        Long deviceId = parseLongNode(node.get("deviceId"));
        Long commandLogId = parseLongNode(node.get("commandLogId"));
        ToolSelection selection = new ToolSelection(deviceId, fleet, commandLogId);
        return selection.hasAnySelection() ? selection : null;
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
