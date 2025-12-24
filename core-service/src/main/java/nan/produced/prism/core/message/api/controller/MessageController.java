package nan.produced.prism.core.message.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.message.api.dto.MarkReadReq;
import nan.produced.prism.core.message.api.dto.MarkReadResp;
import nan.produced.prism.core.message.api.dto.MessageDetailResp;
import nan.produced.prism.core.message.api.dto.MessageListItemResp;
import nan.produced.prism.core.message.api.dto.MessagePageResp;
import nan.produced.prism.core.message.api.dto.UnreadCountResp;
import nan.produced.prism.core.message.application.service.MessageCenterApplicationService;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "消息中心", description = "消息中心（Notifications/Tasks）：落库 + SSE 推送（前端决定展示方式）")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/messages")
public class MessageController {

    private final MessageCenterApplicationService messageCenterApplicationService;

    @Operation(summary = "铃铛：最近消息", description = "用于页面右上角铃铛下拉列表（按创建时间倒序）。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回最近消息",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MessageListItemResp.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/recent")
    public ResponseEntity<BffResponse<List<MessageListItemResp>>> listRecent(
        @RequestParam(value = "kind", required = false) MessageKind kind,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<MessageListItemResp> list = messageCenterApplicationService.listRecent(userId, kind, limit);
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "消息中心：分页查询", description = "支持按类别/类型/状态/已读、时间范围、关键字与关联资源筛选。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回消息分页",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessagePageResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping
    public ResponseEntity<BffResponse<MessagePageResp>> listMessages(
        @RequestParam(value = "kind", required = false) MessageKind kind,
        @RequestParam(value = "type", required = false) String type,
        @RequestParam(value = "status", required = false) MessageStatus status,
        @RequestParam(value = "read", required = false) String read,
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "deviceId", required = false) Long deviceId,
        @RequestParam(value = "programId", required = false) UUID programId,
        @RequestParam(value = "operationId", required = false) String operationId,
        @RequestParam(value = "taskId", required = false) String taskId,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", required = false) Integer size
    ) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        MessagePageResp resp = messageCenterApplicationService.listMessages(
            userId,
            kind,
            type,
            status,
            read,
            from,
            to,
            keyword,
            deviceId,
            programId,
            operationId,
            taskId,
            page,
            size
        );
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取消息详情")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回消息详情",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MessageDetailResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "消息不存在或无权访问")
    @GetMapping("/{messageId}")
    public ResponseEntity<BffResponse<MessageDetailResp>> getMessage(@PathVariable("messageId") UUID messageId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        MessageDetailResp detail = messageCenterApplicationService.getMessage(userId, messageId);
        return ResponseEntity.ok(BffResponse.success(detail).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "获取未读消息数", description = "用于铃铛红点/计数。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回未读数",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = UnreadCountResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/unread-count")
    public ResponseEntity<BffResponse<UnreadCountResp>> countUnread() {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        long unread = messageCenterApplicationService.countUnread(userId);
        return ResponseEntity.ok(BffResponse.success(new UnreadCountResp(unread)).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "批量标记已读")
    @ApiResponse(
        responseCode = "200",
        description = "成功标记已读",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MarkReadResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/read")
    public ResponseEntity<BffResponse<MarkReadResp>> markRead(@RequestBody(required = false) MarkReadReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        int updated = messageCenterApplicationService.markRead(userId, req != null ? req.getIds() : null);
        return ResponseEntity.ok(BffResponse.success(new MarkReadResp(updated)).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "标记单条已读")
    @ApiResponse(
        responseCode = "200",
        description = "成功标记已读",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MarkReadResp.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @PostMapping("/{messageId}/read")
    public ResponseEntity<BffResponse<MarkReadResp>> markReadOne(@PathVariable("messageId") UUID messageId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        int updated = messageCenterApplicationService.markRead(userId, List.of(messageId));
        return ResponseEntity.ok(BffResponse.success(new MarkReadResp(updated)).withTraceId(TraceUtils.getTraceId()));
    }
}
