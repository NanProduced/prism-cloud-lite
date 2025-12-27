package nan.produced.prism.core.telemetry.api.controller;

import io.swagger.v3.oas.annotations.Operation;
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
import nan.produced.prism.core.telemetry.api.dto.sensor.SensorMetricSeriesResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(
    name = "Telemetry - Sensors",
    description = "Monitoring：传感器/监控数据查询接口（面向 SPA，经由 Gateway 访问）。")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/telemetry/sensors")
public class SensorTelemetryController {

    private final SensorTelemetryFacade sensorTelemetryFacade;

    @Operation(summary = "查询传感器指标序列（折线图）", description = "按 serverTime（上报接收时间）过滤，数据保留 30 天。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回序列数据",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = SensorMetricSeriesResponse.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @GetMapping("/series")
    public ResponseEntity<BffResponse<SensorMetricSeriesResponse>> queryMetricSeries(
        @RequestParam("deviceId") Long deviceId,
        @RequestParam(value = "from", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(value = "to", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @RequestParam(value = "sourceType", required = false) String sourceType,
        @RequestParam(value = "reportTypes", required = false) List<String> reportTypes,
        @RequestParam(value = "metricKeys", required = false) List<String> metricKeys,
        @RequestParam(value = "limit", required = false) Integer limit
    ) {
        UUID userId = CloudAuthContext.getCurrentUserUuidAsUuid();
        SensorMetricSeriesResponse resp = sensorTelemetryFacade.queryMetricSeries(
            userId,
            deviceId,
            from != null ? from.toInstant() : null,
            to != null ? to.toInstant() : null,
            sourceType,
            reportTypes,
            metricKeys,
            limit
        );
        return ResponseEntity.ok(BffResponse.success(resp).withTraceId(TraceUtils.getTraceId()));
    }
}
