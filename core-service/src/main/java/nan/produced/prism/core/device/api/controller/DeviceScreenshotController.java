package nan.produced.prism.core.device.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.device.api.dto.DeviceScreenshotItemResp;
import nan.produced.prism.core.device.application.service.DeviceScreenshotApplicationService;
import nan.produced.prism.core.media.application.port.outbound.MediaObjectUrlPort;
import nan.produced.prism.core.security.api.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "设备截图", description = "设备截图查询与管理")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping({"/api/v1/devices"})
public class DeviceScreenshotController {

    private final DeviceScreenshotApplicationService deviceScreenshotApplicationService;
    private final MediaObjectUrlPort mediaObjectUrlPort;

    @Operation(summary = "查询设备全部截图", description = "按时间倒序返回设备截图列表")
    @GetMapping("/{deviceId:\\d+}/screenshots")
    public ResponseEntity<BffResponse<List<DeviceScreenshotItemResp>>> listScreenshots(
            @PathVariable("deviceId") @NotNull Long deviceId) {

        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        var list = deviceScreenshotApplicationService.listScreenshots(userId, deviceId).stream()
                .map(entity -> new DeviceScreenshotItemResp(
                        entity.getScreenshotId(),
                        mediaObjectUrlPort.toPublicUrl(entity.getS3Key()),
                        entity.getSizeBytes(),
                        entity.getUploadedAt(),
                        entity.getContentType()))
                .toList();

        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "删除某张截图", description = "删除截图记录并同步删除对象存储中的文件")
    @PostMapping("/{deviceId:\\d+}/screenshots/{screenshotId}/delete")
    public ResponseEntity<BffResponse<Void>> deleteScreenshot(
            @PathVariable("deviceId") @NotNull Long deviceId,
            @PathVariable("screenshotId") @NotNull UUID screenshotId) {

        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        deviceScreenshotApplicationService.deleteScreenshot(userId, deviceId, screenshotId);
        return ResponseEntity.ok(BffResponse.<Void>success().withTraceId(TraceUtils.getTraceId()));
    }

    @Operation(summary = "清空设备全部截图", description = "删除该设备全部截图记录并同步删除对象存储中的文件")
    @PostMapping("/{deviceId:\\d+}/screenshots/clear")
    public ResponseEntity<BffResponse<Void>> clearScreenshots(
            @PathVariable("deviceId") @NotNull Long deviceId) {

        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        deviceScreenshotApplicationService.clearScreenshots(userId, deviceId);
        return ResponseEntity.ok(BffResponse.<Void>success().withTraceId(TraceUtils.getTraceId()));
    }
}
