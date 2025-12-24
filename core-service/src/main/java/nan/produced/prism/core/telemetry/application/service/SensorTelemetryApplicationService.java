package nan.produced.prism.core.telemetry.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.messaging.FrontendEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.common.messaging.RabbitMessagePublisher;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.device.domain.report.sensor.SensorReportType;
import nan.produced.prism.core.device.domain.report.sensor.SensorType;
import nan.produced.prism.core.device.domain.report.sensor.type.ReceiveCardReport;
import nan.produced.prism.core.telemetry.api.SensorTelemetryFacade;
import nan.produced.prism.core.telemetry.api.dto.sensor.ReceiveCardSampleItem;
import nan.produced.prism.core.telemetry.api.dto.sensor.SensorMetricPoint;
import nan.produced.prism.core.telemetry.api.dto.sensor.SensorMetricSeries;
import nan.produced.prism.core.telemetry.api.dto.sensor.SensorMetricSeriesResponse;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceGpsPointRepository;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceReceiveCardSampleRepository;
import nan.produced.prism.core.telemetry.application.port.outbound.DeviceSensorMetricRepository;
import nan.produced.prism.core.telemetry.application.support.TelemetryQueryWindow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorTelemetryApplicationService implements SensorTelemetryFacade {

    private static final String METRIC_KEY_SENSOR_VALUE = "sensorValue";
    private static final int RETENTION_DAYS = 30;
    private static final int DEFAULT_METRIC_LIMIT = 10000;
    private static final int MAX_METRIC_LIMIT = 200000;
    private static final int DEFAULT_RECEIVE_CARD_LIMIT = 20000;
    private static final int MAX_RECEIVE_CARD_LIMIT = 200000;

    private static final String EVENT_TYPE_SENSOR_REPORTED = "telemetry.sensor.reported";

    private static final String EVENT_TYPE_GPS_REPORTED = "telemetry.gps.reported";

    private final DeviceSensorMetricRepository deviceSensorMetricRepository;
    private final DeviceReceiveCardSampleRepository deviceReceiveCardSampleRepository;
    private final DeviceGpsPointRepository deviceGpsPointRepository;
    private final RabbitMessagePublisher rabbitMessagePublisher;

    @Override
    @Transactional
    public void recordSensorReports(UUID userId, Long deviceId, String sensorData, Instant occurredAt, String traceId) {
        if (userId == null || deviceId == null || sensorData == null || sensorData.isBlank()) {
            return;
        }

        OffsetDateTime serverTime = occurredAt != null
            ? occurredAt.atOffset(ZoneOffset.UTC)
            : OffsetDateTime.now(ZoneOffset.UTC);

        JsonNode root = JsonUtils.fromJson(sensorData);
        if (root == null || !root.isArray() || root.isEmpty()) {
            return;
        }

        List<DeviceSensorMetricRepository.InsertRow> metricRows = new ArrayList<>();
        List<DeviceReceiveCardSampleRepository.InsertRow> receiveCardRows = new ArrayList<>();
        List<DeviceGpsPointRepository.InsertRow> gpsRows = new ArrayList<>();
        List<JsonNode> sensorItems = new ArrayList<>();
        List<Map<String, Object>> gpsPoints = new ArrayList<>();

        for (JsonNode item : root) {
            if (item == null || item.isNull() || !item.isObject()) {
                continue;
            }

            String sensorType = asText(item.get("sensorType"));
            Integer sensorId = asInteger(item.get("sensorId"));
            String reportTimeRaw = asText(item.get("date"));

            SensorReportType resolved = SensorReportType.resolve(sensorType, sensorId);
            if (resolved == null) {
                log.debug("SensorTelemetry - unknown sensor type, skip: deviceId={}, sensorType={}, sensorId={}, traceId={}",
                    deviceId, sensorType, sensorId, traceId);
                continue;
            }

            SensorType sourceType = resolved.getSensorSourceType();
            if (sourceType == null) {
                continue;
            }

            switch (sourceType) {
                case GPS -> appendGpsRow(gpsRows, gpsPoints, userId, deviceId, item, reportTimeRaw, serverTime);
                case RECEIVE_CARD -> {
                    appendReceiveCardRows(receiveCardRows, userId, deviceId, item, reportTimeRaw, serverTime);
                    sensorItems.add(item);
                }
                case DEVICE_SENSOR, M2_SENSOR -> {
                    appendMetricRows(metricRows, userId, deviceId, resolved, item, sensorType, sensorId, reportTimeRaw, serverTime);
                    sensorItems.add(item);
                }
            }
        }

        if (!metricRows.isEmpty()) {
            deviceSensorMetricRepository.insertBatch(metricRows);
        }
        if (!receiveCardRows.isEmpty()) {
            deviceReceiveCardSampleRepository.insertBatch(receiveCardRows);
        }
        if (!gpsRows.isEmpty()) {
            deviceGpsPointRepository.insertBatch(gpsRows);
        }

        publishRealtimeEvents(userId, deviceId, sensorItems, gpsPoints, serverTime.toInstant(), traceId);
    }

    private void publishRealtimeEvents(UUID userId,
                                       Long deviceId,
                                       List<JsonNode> sensorItems,
                                       List<Map<String, Object>> gpsPoints,
                                       Instant serverTime,
                                       String traceId) {
        if (userId == null || deviceId == null) {
            return;
        }

        if (sensorItems != null && !sensorItems.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("items", sensorItems);

            FrontendEventMessage message = FrontendEventMessage.builder()
                    .success(true)
                    .type(EVENT_TYPE_SENSOR_REPORTED)
                    .scope(FrontendEventMessage.Scope.builder()
                            .userId(userId)
                            .deviceId(deviceId)
                            .build())
                    .data(data)
                    .occurredAt(serverTime)
                    .traceId(traceId)
                    .build();

            rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.REALTIME_SENSOR_REPORTED, message);
        }

        if (gpsPoints != null && !gpsPoints.isEmpty()) {
            Map<String, Object> data = new HashMap<>();
            data.put("points", gpsPoints);

            FrontendEventMessage message = FrontendEventMessage.builder()
                    .success(true)
                    .type(EVENT_TYPE_GPS_REPORTED)
                    .scope(FrontendEventMessage.Scope.builder()
                            .userId(userId)
                            .deviceId(deviceId)
                            .build())
                    .data(data)
                    .occurredAt(serverTime)
                    .traceId(traceId)
                    .build();

            rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.REALTIME_GPS_REPORTED, message);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SensorMetricSeriesResponse queryMetricSeries(UUID userId,
                                                        Long deviceId,
                                                        Instant from,
                                                        Instant to,
                                                        String sourceType,
                                                        List<String> reportTypes,
                                                        List<String> metricKeys,
                                                        Integer limit) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (deviceId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId is required");
        }

        TelemetryQueryWindow.TimeWindow window = TelemetryQueryWindow.clamp(from, to, RETENTION_DAYS, 1);
        if (window.to().isBefore(window.from())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "`to` must be after `from`");
        }
        int safeLimit = clampLimit(limit, DEFAULT_METRIC_LIMIT, MAX_METRIC_LIMIT);

        List<DeviceSensorMetricRepository.PointRow> rows = deviceSensorMetricRepository.listPoints(
            userId,
            deviceId,
            window.from(),
            window.to(),
            sourceType,
            reportTypes,
            metricKeys,
            safeLimit
        );

        if (rows == null || rows.isEmpty()) {
            return new SensorMetricSeriesResponse(deviceId, window.from().toInstant(), window.to().toInstant(), List.of());
        }

        record SeriesKey(String sourceType, String reportType, String metricKey) {
        }

        Map<SeriesKey, List<SensorMetricPoint>> pointsBySeries = new java.util.LinkedHashMap<>();
        for (DeviceSensorMetricRepository.PointRow row : rows) {
            if (row == null || row.serverTime() == null) {
                continue;
            }
            SeriesKey key = new SeriesKey(row.sourceType(), row.reportType(), row.metricKey());
            pointsBySeries.computeIfAbsent(key, k -> new ArrayList<>())
                .add(new SensorMetricPoint(row.serverTime().toInstant(), row.valueNum()));
        }

        List<SensorMetricSeries> series = pointsBySeries.entrySet().stream()
            .map(e -> new SensorMetricSeries(
                e.getKey().sourceType(),
                e.getKey().reportType(),
                e.getKey().metricKey(),
                e.getValue()
            ))
            .toList();

        return new SensorMetricSeriesResponse(deviceId, window.from().toInstant(), window.to().toInstant(), series);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceiveCardSampleItem> listReceiveCardSamples(UUID userId,
                                                              Long deviceId,
                                                              Instant from,
                                                              Instant to,
                                                              Integer netPortNum,
                                                              Integer receiveCardNum,
                                                              Integer limit) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (deviceId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId is required");
        }

        TelemetryQueryWindow.TimeWindow window = TelemetryQueryWindow.clamp(from, to, RETENTION_DAYS, 1);
        if (window.to().isBefore(window.from())) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "`to` must be after `from`");
        }
        int safeLimit = clampLimit(limit, DEFAULT_RECEIVE_CARD_LIMIT, MAX_RECEIVE_CARD_LIMIT);

        List<DeviceReceiveCardSampleRepository.SampleRow> rows = deviceReceiveCardSampleRepository.listSamples(
            userId,
            deviceId,
            window.from(),
            window.to(),
            netPortNum,
            receiveCardNum,
            safeLimit
        );

        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<ReceiveCardSampleItem> list = new ArrayList<>(rows.size());
        for (DeviceReceiveCardSampleRepository.SampleRow row : rows) {
            if (row == null || row.serverTime() == null) {
                continue;
            }
            list.add(new ReceiveCardSampleItem(
                row.netPortNum(),
                row.receiveCardNum(),
                row.bitErrorRate(),
                row.temperature(),
                row.humidity(),
                row.smoke(),
                row.serverTime().toInstant()
            ));
        }
        return list;
    }

    private int clampLimit(Integer limit, int defaultLimit, int maxLimit) {
        int value = limit == null ? defaultLimit : limit;
        if (value <= 0) {
            value = defaultLimit;
        }
        return Math.min(maxLimit, value);
    }

    private void appendMetricRows(List<DeviceSensorMetricRepository.InsertRow> out,
                                  UUID userId,
                                  Long deviceId,
                                  SensorReportType resolved,
                                  JsonNode item,
                                  String sensorType,
                                  Integer sensorId,
                                  String reportTimeRaw,
                                  OffsetDateTime serverTime) {
        if (out == null || resolved == null || item == null) {
            return;
        }

        String sourceType = resolved.getSensorSourceType().name();
        String reportType = resolved.getReportType();
        if (reportType == null || reportType.isBlank()) {
            return;
        }

        // brightness is multi-metric
        if ("bright".equals(sensorType)) {
            addMetricIfPresent(out, userId, deviceId, sourceType, reportType, sensorType, sensorId,
                "masterBrightValue", asDouble(item.get("masterBrightValue")), reportTimeRaw, serverTime);
            addMetricIfPresent(out, userId, deviceId, sourceType, reportType, sensorType, sensorId,
                "screenBrightValue", asDouble(item.get("screenBrightValue")), reportTimeRaw, serverTime);
            addMetricIfPresent(out, userId, deviceId, sourceType, reportType, sensorType, sensorId,
                "sensorBrightValue", asDouble(item.get("sensorBrightValue")), reportTimeRaw, serverTime);
            return;
        }

        Double value = asDouble(item.get(METRIC_KEY_SENSOR_VALUE));
        addMetricIfPresent(out, userId, deviceId, sourceType, reportType, sensorType, sensorId,
            METRIC_KEY_SENSOR_VALUE, value, reportTimeRaw, serverTime);
    }

    private void addMetricIfPresent(List<DeviceSensorMetricRepository.InsertRow> out,
                                    UUID userId,
                                    Long deviceId,
                                    String sourceType,
                                    String reportType,
                                    String sensorType,
                                    Integer sensorId,
                                    String metricKey,
                                    Double valueNum,
                                    String reportTimeRaw,
                                    OffsetDateTime serverTime) {
        if (valueNum == null) {
            return;
        }
        out.add(new DeviceSensorMetricRepository.InsertRow(
            userId,
            deviceId,
            sourceType,
            reportType,
            sensorType,
            sensorId,
            metricKey,
            valueNum,
            reportTimeRaw,
            serverTime
        ));
    }

    private void appendReceiveCardRows(List<DeviceReceiveCardSampleRepository.InsertRow> out,
                                       UUID userId,
                                       Long deviceId,
                                       JsonNode item,
                                       String reportTimeRaw,
                                       OffsetDateTime serverTime) {
        ReceiveCardReport report = JsonUtils.convertValue(item, ReceiveCardReport.class);
        if (report == null || report.getSensorValue() == null || report.getSensorValue().isEmpty()) {
            return;
        }

        for (ReceiveCardReport.ReceiveCardValue value : report.getSensorValue()) {
            if (value == null || value.getReceiveCards() == null || value.getReceiveCards().isEmpty()) {
                continue;
            }
            Integer netPortNum = value.getNetPortNum();
            for (ReceiveCardReport.ReceiveCardInfo card : value.getReceiveCards()) {
                if (card == null) {
                    continue;
                }
                out.add(new DeviceReceiveCardSampleRepository.InsertRow(
                    userId,
                    deviceId,
                    netPortNum,
                    card.getReceiveCardNum(),
                    card.getX(),
                    card.getY(),
                    card.getWidth(),
                    card.getHeight(),
                    card.getBitErrorRate(),
                    card.getTemperature(),
                    card.getHumidity(),
                    card.getSmoke(),
                    reportTimeRaw,
                    serverTime
                ));
            }
        }
    }

    private void appendGpsRow(List<DeviceGpsPointRepository.InsertRow> out,
                              List<Map<String, Object>> outRealtime,
                              UUID userId,
                              Long deviceId,
                              JsonNode item,
                              String reportTimeRaw,
                              OffsetDateTime serverTime) {
        Double lon = asDouble(item.get("longitude"));
        Double lat = asDouble(item.get("latitude"));
        if (lon == null || lat == null) {
            return;
        }
        if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            return;
        }

        ObjectNode extra = JsonNodeFactory.instance.objectNode();
        if (item.hasNonNull("cellInfo")) {
            extra.set("cellInfo", item.get("cellInfo"));
        }
        if (item.hasNonNull("gsv")) {
            extra.set("gsv", item.get("gsv"));
        }

        if (outRealtime != null) {
            Map<String, Object> point = new HashMap<>();
            point.put("longitude", lon);
            point.put("latitude", lat);
            Float accuracy = asFloat(item.get("accuracy"));
            if (accuracy != null) {
                point.put("accuracy", accuracy);
            }
            Float altitude = asFloat(item.get("altitude"));
            if (altitude != null) {
                point.put("altitude", altitude);
            }
            Float speed = asFloat(item.get("speed"));
            if (speed != null) {
                point.put("speed", speed);
            }
            Double direct = asDouble(item.get("direct"));
            if (direct != null) {
                point.put("direct", direct);
            }
            Integer satellites = asInteger(item.get("satellites"));
            if (satellites != null) {
                point.put("satellites", satellites);
            }
            if (reportTimeRaw != null) {
                point.put("date", reportTimeRaw);
            }
            if (serverTime != null) {
                point.put("serverTime", serverTime.toInstant());
            }
            if (item.hasNonNull("cellInfo")) {
                point.put("cellInfo", item.get("cellInfo"));
            }
            if (item.hasNonNull("gsv")) {
                point.put("gsv", item.get("gsv"));
            }
            outRealtime.add(point);
        }

        out.add(new DeviceGpsPointRepository.InsertRow(
            userId,
            deviceId,
            lon,
            lat,
            asFloat(item.get("accuracy")),
            asFloat(item.get("altitude")),
            asFloat(item.get("speed")),
            asDouble(item.get("direct")),
            asInteger(item.get("satellites")),
            reportTimeRaw,
            serverTime,
            extra.isEmpty() ? null : JsonUtils.toJson(extra)
        ));
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer asInteger(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.intValue();
        }
        String text = asText(node);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Double asDouble(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.doubleValue();
        }
        String text = asText(node);
        if (text == null) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ignore) {
            return null;
        }
    }

    private Float asFloat(JsonNode node) {
        Double value = asDouble(node);
        if (value == null) {
            return null;
        }
        return value.floatValue();
    }
}
