package nan.produced.prism.core.telemetry.api.controller;

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
import nan.produced.prism.core.security.api.CloudAuthContext;
import nan.produced.prism.core.telemetry.api.SensorTelemetryFacade;
import nan.produced.prism.core.telemetry.api.dto.sensor.ReceiveCardSampleItem;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Telemetry - Receive Cards",
    description = "Monitoring：接收卡监控数据查询接口（面向 SPA，经由 Gateway 访问）。")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/telemetry/receive-cards")
public class ReceiveCardTelemetryController {

    private final SensorTelemetryFacade sensorTelemetryFacade;

    @Operation(summary = "查询接收卡采样点（扁平化）", description = "建议指定 netPortNum + receiveCardNum 以避免数据量过大。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回采样点",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ReceiveCardSampleItem.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/samples")
    public ResponseEntity<BffResponse<List<ReceiveCardSampleItem>>> listSamples(
        @RequestParam("deviceId") Long deviceId,
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(value = "netPortNum", required = false) Integer netPortNum,
        @RequestParam(value = "receiveCardNum", required = false) Integer receiveCardNum,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        List<ReceiveCardSampleItem> list = sensorTelemetryFacade.listReceiveCardSamples(
            userId,
            deviceId,
            from != null ? from.toInstant() : null,
            to != null ? to.toInstant() : null,
            netPortNum,
            receiveCardNum,
            limit
        );
        return ResponseEntity.ok(BffResponse.success(list).withTraceId(TraceUtils.getTraceId()));
    }
}
