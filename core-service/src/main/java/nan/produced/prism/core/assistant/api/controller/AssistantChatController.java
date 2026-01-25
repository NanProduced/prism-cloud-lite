package nan.produced.prism.core.assistant.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.assistant.application.chat.AssistantChatFacade;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@Tag(name = "AI Assistant", description = "AI 助手聊天（Vercel AI SDK 6 UIMessage Stream v1 · SSE）")
@RestController
@RequiredArgsConstructor
public class AssistantChatController {

    private final AssistantChatFacade assistantChatFacade;

    @Operation(
            summary = "AI 助手聊天（UIMessage Stream v1 / SSE）",
            description = """
                    - Endpoint：`POST /api/chat`
                    - Response：`text/event-stream; charset=utf-8`
                      - Header：`x-vercel-ai-ui-message-stream: v1`
                      - Body：UIMessage Stream Protocol(v1)，SSE JSON 事件流：每条事件 `data: <payload>\\n\\n`，以 `data: [DONE]\\n\\n` 结束
                    - Request：AI SDK 6 `useChat` 默认发送 `messages: UIMessage[]`（含 `parts`）；后端仅解析 `role=user|assistant` 的内容；忽略客户端传入的 `system` 以防 prompt injection。
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
                                        { "id": "msg_user_1", "role": "user", "parts": [ { "type": "text", "text": "帮我解释一下订阅等级有什么区别？" } ] }
                                      ]
                                    }
                                    """
                    )
            )
    )
    @ApiResponse(
            responseCode = "200",
            description = "UIMessage Stream v1（SSE）流式返回（按 `data: {type:...}` 输出；以 `finish` + `[DONE]` 结束）",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string"),
                    examples = @ExampleObject(
                            name = "UiMessageStreamSse",
                            value = """
                                    data: {"type":"text-delta","id":"text-1","delta":"你好，我是 Prism Cloud AI 助手。"}
                                                                    
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
    public ResponseEntity<StreamingResponseBody> chat(@RequestBody JsonNode request) {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        String tier = user.tier();

        StreamingResponseBody body = outputStream -> assistantChatFacade.handle(userId, tier, request, outputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-vercel-ai-ui-message-stream", "v1");
        headers.add(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.add(HttpHeaders.CONNECTION, "keep-alive");
        headers.add("x-accel-buffering", "no");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/event-stream; charset=utf-8"))
                .body(body);
    }
}
