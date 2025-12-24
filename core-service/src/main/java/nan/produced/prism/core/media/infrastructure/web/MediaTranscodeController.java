package nan.produced.prism.core.media.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.media.application.dto.TranscodeCreateRequest;
import nan.produced.prism.core.media.application.dto.TranscodeCreateResponse;
import nan.produced.prism.core.media.application.dto.TranscodeRetryRequest;
import nan.produced.prism.core.media.application.dto.TranscodeRetryResponse;
import nan.produced.prism.core.media.application.service.MediaTranscodeService;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/media-library")
@Tag(name = "素材库-转码", description = "素材转码任务（面向 SPA，经由 Gateway 访问）")
public class MediaTranscodeController {

    private final MediaTranscodeService mediaTranscodeService;

    @PostMapping("/assets/{assetId}/transcode")
    @Operation(
        summary = "创建素材转码任务",
        description = """
            为指定素材创建转码任务（当前仅支持视频素材）。

            - `presetId`：转码预设（需在 `prism.media.transcode.presets` 中配置）；
            - 创建成功会写入消息中心（Message Center），返回 `messageId` 用于前端展示进度/结果；
            - 响应体为 `BffResponse<TranscodeCreateResponse>`。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功创建转码任务",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = TranscodeCreateResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数不合法或仅支持视频素材")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "素材/文件夹不存在或无权访问")
    public ResponseEntity<BffResponse<TranscodeCreateResponse>> transcode(
        @Parameter(description = "素材ID") @PathVariable("assetId") String assetId,
        @RequestBody @Validated TranscodeCreateRequest request) {

        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());

        var response = mediaTranscodeService.createTranscodeTask(userId, user.tier(), assetId, request);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    @PostMapping("/transcode/{taskId}/retry")
    @Operation(
        summary = "重试转码任务",
        description = """
            重试指定 taskId 的转码任务。

            - 需要重新提供 `assetId` 和 `presetId`，便于服务端校验素材类型与预设；
            - 重试会生成新的消息中心任务消息（新的 `messageId`），用于展示新的进度/结果；
            - 响应体为 `BffResponse<TranscodeRetryResponse>`。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功重试转码任务",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = TranscodeRetryResponse.class)))
    @ApiResponse(responseCode = "400", description = "参数不合法或仅支持视频素材")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "任务/素材/文件夹不存在或无权访问")
    public ResponseEntity<BffResponse<TranscodeRetryResponse>> retry(
        @Parameter(description = "转码任务ID（以 transcode- 开头）") @PathVariable("taskId") String taskId,
        @RequestBody @Validated TranscodeRetryRequest request) {

        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());

        var response = mediaTranscodeService.retryTranscodeTask(userId, user.tier(), taskId, request);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }
}
