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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

@Tag(name = "AI Assistant", description = "AI 助手聊天（Vercel AI SDK 5.0 Data Stream Protocol v1）")
@RestController
@RequiredArgsConstructor
public class AssistantChatController {

    private final AssistantChatService assistantChatService;

    @Operation(
            summary = "AI 助手聊天（Data Stream v1）",
            description = """
                    - Endpoint：`POST /api/chat`
                    - Response：`text/plain; charset=utf-8`
                      - Header：`x-vercel-ai-data-stream: v1`
                      - Body：Vercel AI SDK Data Stream Protocol(v1)，每行一个 chunk：`<tag>:<payload>\\n`
                    - Request：OpenAI-like messages，仅解析 `messages[]` 中 `role=user|assistant` 的消息；忽略客户端传入的 `system` 以防 prompt injection。
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
            description = "Data Stream Protocol(v1) 流式返回（按行输出 0/1/b/c/2/3/d chunk；以 d 结束）",
            content = @Content(
                    mediaType = MediaType.TEXT_PLAIN_VALUE,
                    schema = @Schema(type = "string"),
                    examples = @ExampleObject(
                            name = "DataStreamLines",
                            value = """
                                    0:"你好，我是 Prism Cloud AI 助手。"
                                    d:{"finishReason":"stop"}
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
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<StreamingResponseBody> chat(@RequestBody JsonNode request) {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        String tier = user.tier();

        StreamingResponseBody body = outputStream -> assistantChatService.handle(userId, tier, request, outputStream);

        HttpHeaders headers = new HttpHeaders();
        headers.add("x-vercel-ai-data-stream", "v1");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("text/plain; charset=utf-8"))
                .body(body);
    }
}
