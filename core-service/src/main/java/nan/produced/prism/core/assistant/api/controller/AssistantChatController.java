package nan.produced.prism.core.assistant.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.chat.AssistantChatService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Tag(name = "AI Assistant", description = "AI 助手聊天（SSE 流式输出，Vercel AI SDK-compatible 请求体）")
@RestController
@RequiredArgsConstructor
public class AssistantChatController {

    private final AssistantChatService assistantChatService;

    @Operation(
            summary = "AI 助手聊天（SSE）",
            description = """
                    - Endpoint：`POST /api/chat`
                    - Response：`text/event-stream`
                      - 每条 SSE `data:` 为一个 JSON chunk（UIMessageChunk-like）
                      - 最终以 `data: [DONE]` 结束
                    - Request：Vercel AI SDK-like，仅解析 `messages[]` 中 `role=user|assistant` 的消息；忽略客户端传入的 `system` 以防 prompt injection。
                    """)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(type = "object"),
                    examples = @ExampleObject(
                            name = "VercelAiSdkLike",
                            value = """
                                    {
                                      "messages": [
                                        { "role": "user", "content": "帮我解释一下订阅等级有什么区别？" }
                                      ]
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "SSE 流式返回（每个 data 行是一段 JSON；以 [DONE] 结束）",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string"),
                    examples = @ExampleObject(
                            name = "SseDataLines",
                            value = """
                                    data: {"type":"start"}

                                    data: {"type":"text-start","id":"text-1"}

                                    data: {"type":"text-delta","id":"text-1","delta":"..."} 

                                    data: {"type":"text-end","id":"text-1"}

                                    data: {"type":"finish","finishReason":"stop"}

                                    data: [DONE]

                                    """
                    )
            )
    )
    @ApiResponse(responseCode = "400", description = "请求体缺失或格式错误（例如缺少 messages）")
    @ApiResponse(responseCode = "401", description = "未登录（CLOUD_AUTH 头缺失/无效或会话失效）")
    @ApiResponse(responseCode = "403", description = "无权限（订阅/角色限制）")
    @PostMapping(
            path = "/api/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
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
