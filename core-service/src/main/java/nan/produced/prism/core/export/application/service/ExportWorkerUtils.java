package nan.produced.prism.core.export.application.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import nan.produced.prism.core.export.api.dto.ExportValueType;
import nan.produced.prism.core.export.infrastructure.persistence.CommandLogsExportRepository;
import nan.produced.prism.core.export.infrastructure.persistence.DeviceLogsExportRepository;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineSessionItem;
import nan.produced.prism.core.telemetry.api.dto.playback.MediaPlaySessionItem;
import nan.produced.prism.core.telemetry.api.dto.playback.ProgramPlaySessionItem;
import org.springframework.util.StringUtils;

final class ExportWorkerUtils {

    private static final DateTimeFormatter TEXT_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExportWorkerUtils() {
    }

    static Locale parseLocale(String locale) {
        if (!StringUtils.hasText(locale)) {
            return Locale.ENGLISH;
        }
        Locale parsed = Locale.forLanguageTag(locale.trim());
        return parsed != null ? parsed : Locale.ENGLISH;
    }

    static ZoneId parseZoneId(String tz) {
        if (!StringUtils.hasText(tz)) {
            return ZoneOffset.UTC;
        }
        String trimmed = tz.trim();
        if ("Z".equalsIgnoreCase(trimmed) || "UTC".equalsIgnoreCase(trimmed)) {
            return ZoneOffset.UTC;
        }
        String normalizedOffset = tryNormalizeOffset(trimmed);
        String candidate = normalizedOffset != null ? normalizedOffset : trimmed;
        try {
            return ZoneId.of(candidate);
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }

    private static String tryNormalizeOffset(String tz) {
        String candidate = tz;
        if (candidate.regionMatches(true, 0, "UTC", 0, 3) && candidate.length() > 3) {
            candidate = candidate.substring(3);
        }
        candidate = candidate.trim();
        if (!(candidate.startsWith("+") || candidate.startsWith("-"))) {
            return null;
        }
        char sign = candidate.charAt(0);
        String rest = candidate.substring(1);
        Integer hour;
        Integer minute;

        if (rest.contains(":")) {
            String[] parts = rest.split(":", -1);
            if (parts.length != 2) {
                return null;
            }
            hour = parseInt(parts[0]);
            minute = parseInt(parts[1]);
        } else if (rest.length() == 4) {
            hour = parseInt(rest.substring(0, 2));
            minute = parseInt(rest.substring(2, 4));
        } else {
            hour = parseInt(rest);
            minute = 0;
        }
        if (hour == null || minute == null) {
            return null;
        }
        try {
            return ZoneOffset.ofHoursMinutes(sign == '-' ? -hour : hour, sign == '-' ? -minute : minute).getId();
        } catch (Exception ignore) {
            return null;
        }
    }

    private static Integer parseInt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static OffsetDateTime requireOffsetDateTime(JsonNode filters, String key) {
        String value = textOrThrow(filters, key);
        return OffsetDateTime.parse(value);
    }

    static Instant requireInstant(JsonNode filters, String key) {
        String value = textOrThrow(filters, key);
        return Instant.parse(value);
    }

    static Long requireLong(JsonNode filters, String key) {
        Long value = longOrNull(filters, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    static Long longOrNull(JsonNode filters, String key) {
        if (filters == null || !StringUtils.hasText(key)) {
            return null;
        }
        JsonNode node = filters.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (StringUtils.hasText(node.asText())) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    static Integer intOrNull(JsonNode filters, String key) {
        if (filters == null || !StringUtils.hasText(key)) {
            return null;
        }
        JsonNode node = filters.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.intValue();
        }
        if (StringUtils.hasText(node.asText())) {
            try {
                return Integer.parseInt(node.asText().trim());
            } catch (Exception ignore) {
            }
        }
        return null;
    }

    static UUID uuidOrNull(JsonNode filters, String key) {
        if (filters == null || !StringUtils.hasText(key)) {
            return null;
        }
        JsonNode node = filters.get(key);
        if (node == null || node.isNull() || !StringUtils.hasText(node.asText())) {
            return null;
        }
        try {
            return UUID.fromString(node.asText().trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    static Boolean boolOrNull(JsonNode filters, String key) {
        if (filters == null || !StringUtils.hasText(key)) {
            return null;
        }
        JsonNode node = filters.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (StringUtils.hasText(node.asText())) {
            String s = node.asText().trim().toLowerCase(Locale.ROOT);
            if ("true".equals(s) || "1".equals(s)) {
                return true;
            }
            if ("false".equals(s) || "0".equals(s)) {
                return false;
            }
        }
        return null;
    }

    static String textOrNull(JsonNode filters, String key) {
        if (filters == null || !StringUtils.hasText(key)) {
            return null;
        }
        JsonNode node = filters.get(key);
        if (node == null || node.isNull()) {
            return null;
        }
        String s = node.asText(null);
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    static String textOrThrow(JsonNode filters, String key) {
        String s = textOrNull(filters, key);
        if (!StringUtils.hasText(s)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return s.trim();
    }

    static List<Integer> intList(JsonNode filters, String key) {
        if (filters == null || !StringUtils.hasText(key)) {
            return List.of();
        }
        JsonNode node = filters.get(key);
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<Integer> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            Integer v = item.isNumber() ? item.intValue() : parseInt(item.asText());
            if (v != null) {
                list.add(v);
            }
        }
        return list;
    }

    static List<String> stringList(JsonNode filters, String key) {
        if (filters == null || !StringUtils.hasText(key)) {
            return List.of();
        }
        JsonNode node = filters.get(key);
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            String s = item.asText(null);
            if (StringUtils.hasText(s)) {
                list.add(s.trim());
            }
        }
        return list;
    }

    static Object extractDeviceLog(DeviceLogsExportRepository.Row row, String key, ExportValueType valueType, ZoneId userZone) {
        if (row == null) {
            return null;
        }
        return switch (key) {
            case "id" -> String.valueOf(row.id());
            case "deviceId" -> String.valueOf(row.deviceId());
            case "deviceName" -> row.deviceName();
            case "operationId" -> row.operationId();
            case "level" -> row.level();
            case "logType" -> row.logType();
            case "categories" -> row.categories();
            case "description" -> row.description();
            case "deviceTimeRaw" -> row.deviceTimeRaw();
            case "reportTime" -> toExcelDate(row.reportTime(), userZone);
            case "createdAt" -> toExcelDate(row.createdAt(), userZone);
            default -> null;
        };
    }

    static Object extractCommandLog(CommandLogsExportRepository.Row row, String key, ExportValueType valueType, ZoneId userZone) {
        if (row == null) {
            return null;
        }
        return switch (key) {
            case "id" -> String.valueOf(row.id());
            case "deviceId" -> String.valueOf(row.deviceId());
            case "deviceName" -> row.deviceName();
            case "operationId" -> row.operationId() != null ? row.operationId().toString() : null;
            case "actionType" -> row.actionType();
            case "trackingLevel" -> row.trackingLevel();
            case "status" -> row.status();
            case "accepted" -> row.accepted();
            case "covered" -> row.covered();
            case "sendMethod" -> row.sendMethod();
            case "queuedId" -> row.queuedId();
            case "errorMessage" -> row.errorMessage();
            case "createdAt" -> toExcelDate(row.createdAt(), userZone);
            case "updatedAt" -> toExcelDate(row.updatedAt(), userZone);
            case "payload" -> row.payload();
            default -> null;
        };
    }

    static Object extractOnlineSession(ExportWorkerService.OnlineSessionRow row, String key, ExportValueType valueType, ZoneId userZone) {
        if (row == null || row.item() == null) {
            return null;
        }
        DeviceOnlineSessionItem item = row.item();
        return switch (key) {
            case "sessionId" -> String.valueOf(item.sessionId());
            case "deviceId" -> String.valueOf(row.deviceId());
            case "deviceName" -> row.deviceName();
            case "onlineAt" -> toExcelDate(item.onlineAt() != null ? OffsetDateTime.ofInstant(item.onlineAt(), ZoneOffset.UTC) : null, userZone);
            case "offlineAt" -> toExcelDate(item.offlineAt() != null ? OffsetDateTime.ofInstant(item.offlineAt(), ZoneOffset.UTC) : null, userZone);
            case "effectiveOnlineAt" -> toExcelDate(item.effectiveOnlineAt() != null ? OffsetDateTime.ofInstant(item.effectiveOnlineAt(), ZoneOffset.UTC) : null, userZone);
            case "effectiveOfflineAt" -> toExcelDate(item.effectiveOfflineAt() != null ? OffsetDateTime.ofInstant(item.effectiveOfflineAt(), ZoneOffset.UTC) : null, userZone);
            case "onlineSecondsInRange" -> item.onlineSecondsInRange();
            default -> null;
        };
    }

    static Object extractProgramPlaySession(ProgramPlaySessionItem row,
                                            String key,
                                            ExportValueType valueType,
                                            ZoneId userZone) {
        if (row == null) {
            return null;
        }
        return switch (key) {
            case "id" -> String.valueOf(row.id());
            case "deviceId" -> String.valueOf(row.deviceId());
            case "deviceName" -> row.deviceName();
            case "lan" -> row.lan();
            case "lanProgramId" -> row.lanProgramId();
            case "programId" -> row.programId() != null ? row.programId().toString() : null;
            case "releaseVersion" -> row.releaseVersion();
            case "programName" -> row.programNameSnapshot();
            case "programVsn" -> row.programVsn();
            case "startAt" -> toExcelDate(row.startAt(), userZone);
            case "endAt" -> toExcelDate(row.endAt(), userZone);
            case "effectiveStartAt" -> toExcelDate(row.effectiveStartAt(), userZone);
            case "effectiveEndAt" -> toExcelDate(row.effectiveEndAt(), userZone);
            case "playSecondsInRange" -> row.playSecondsInRange();
            case "createdAt" -> toExcelDate(row.createdAt(), userZone);
            default -> null;
        };
    }

    static Object extractMediaPlaySession(MediaPlaySessionItem row,
                                          String key,
                                          ExportValueType valueType,
                                          ZoneId userZone) {
        if (row == null) {
            return null;
        }
        return switch (key) {
            case "id" -> String.valueOf(row.id());
            case "deviceId" -> String.valueOf(row.deviceId());
            case "deviceName" -> row.deviceName();
            case "lan" -> row.lan();
            case "mediaId" -> row.mediaId();
            case "itemType" -> row.itemType();
            case "resOriginName" -> row.resOriginName();
            case "resMd5Name" -> row.resMd5Name();
            case "programId" -> row.programId() != null ? row.programId().toString() : null;
            case "releaseVersion" -> row.releaseVersion();
            case "programName" -> row.programNameSnapshot();
            case "programVsn" -> row.programVsn();
            case "pageName" -> row.pageName();
            case "pageIndex" -> row.pageIndex();
            case "regionName" -> row.regionName();
            case "regionIndex" -> row.regionIndex();
            case "startAt" -> toExcelDate(row.startAt(), userZone);
            case "endAt" -> toExcelDate(row.endAt(), userZone);
            case "effectiveStartAt" -> toExcelDate(row.effectiveStartAt(), userZone);
            case "effectiveEndAt" -> toExcelDate(row.effectiveEndAt(), userZone);
            case "playSecondsInRange" -> row.playSecondsInRange();
            case "reportedDuration" -> row.reportedDuration();
            case "createdAt" -> toExcelDate(row.createdAt(), userZone);
            default -> null;
        };
    }

    static String formatText(Object v, ExportValueType valueType) {
        if (v == null) {
            return "";
        }
        if (v instanceof Date d) {
            LocalDateTime ldt = LocalDateTime.ofInstant(d.toInstant(), ZoneOffset.UTC);
            return TEXT_DATETIME_FORMAT.format(ldt);
        }
        return String.valueOf(v);
    }

    static void writeJsonValue(JsonGenerator gen, Object v) throws Exception {
        if (v == null) {
            gen.writeNull();
            return;
        }
        if (v instanceof Boolean b) {
            gen.writeBoolean(b);
            return;
        }
        if (v instanceof Integer i) {
            gen.writeNumber(i);
            return;
        }
        if (v instanceof Long l) {
            gen.writeNumber(l);
            return;
        }
        if (v instanceof Double d) {
            gen.writeNumber(d);
            return;
        }
        if (v instanceof Date d) {
            gen.writeString(Instant.ofEpochMilli(d.getTime()).toString());
            return;
        }
        gen.writeString(String.valueOf(v));
    }

    static Date toExcelDate(OffsetDateTime utcTime, ZoneId userZone) {
        if (utcTime == null) {
            return null;
        }
        LocalDateTime local = utcTime.toInstant().atZone(userZone).toLocalDateTime();
        return Date.from(local.toInstant(ZoneOffset.UTC));
    }
}
