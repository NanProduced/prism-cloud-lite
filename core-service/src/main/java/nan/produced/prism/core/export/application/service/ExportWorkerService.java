package nan.produced.prism.core.export.application.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.export.api.ExportFormat;
import nan.produced.prism.core.export.api.ExportType;
import nan.produced.prism.core.export.api.dto.ExportFieldDefinition;
import nan.produced.prism.core.export.domain.ExportFileEntity;
import nan.produced.prism.core.export.infrastructure.config.ExportProperties;
import nan.produced.prism.core.export.infrastructure.config.ExportProperties.TierLimits;
import nan.produced.prism.core.export.infrastructure.messaging.ExportTaskPendingMessage;
import nan.produced.prism.core.export.infrastructure.persistence.CommandLogsExportRepository;
import nan.produced.prism.core.export.infrastructure.persistence.DeviceLogsExportRepository;
import nan.produced.prism.core.export.infrastructure.persistence.ExportFileRepositoryJpa;
import nan.produced.prism.core.message.api.MessageCenterConstants;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import nan.produced.prism.core.media.application.port.outbound.ObjectStoragePort;
import nan.produced.prism.core.telemetry.api.DeviceOnlineTimeFacade;
import nan.produced.prism.core.telemetry.api.PlaybackTelemetryFacade;
import nan.produced.prism.core.telemetry.api.dto.DeviceOnlineSessionItem;
import nan.produced.prism.core.telemetry.api.dto.playback.MediaPlaySessionItem;
import nan.produced.prism.core.telemetry.api.dto.playback.ProgramPlaySessionItem;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageQuotaQueryFacade;
import nan.produced.prism.core.user.api.UserStorageQuotaSnapshot;
import nan.produced.prism.core.user.api.UserStorageUsageFacade;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.avro.LogicalTypes;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.OutputFile;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportWorkerService {

    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final ExportProperties exportProperties;
    private final ExportSchemaRegistry exportSchemaRegistry;
    private final ExportFileRepositoryJpa exportFileRepositoryJpa;

    private final MessageCenterFacade messageCenterFacade;
    private final MessageSource messageSource;

    private final DeviceLogsExportRepository deviceLogsExportRepository;
    private final CommandLogsExportRepository commandLogsExportRepository;
    private final DeviceOnlineTimeFacade deviceOnlineTimeFacade;
    private final DeviceRepository deviceRepository;
    private final PlaybackTelemetryFacade playbackTelemetryFacade;

    private final UserStorageQuotaQueryFacade userStorageQuotaQueryFacade;
    private final UserStorageUsageFacade userStorageUsageFacade;

    private final ObjectStoragePort objectStoragePort;

    public void handle(ExportTaskPendingMessage message) {
        if (message == null || message.userId() == null || message.messageId() == null || message.exportId() == null) {
            return;
        }
        if (!StringUtils.hasText(message.taskId()) || message.exportType() == null || message.format() == null) {
            fail(message, "INVALID_ARGUMENT", "任务参数不完整");
            return;
        }

        ExportFileEntity export = exportFileRepositoryJpa.findByIdAndUserId(message.exportId(), message.userId()).orElse(null);
        if (export == null) {
            fail(message, "NOT_FOUND", "export not found");
            return;
        }
        if (export.getDeletedAt() != null) {
            return;
        }
        if (export.getCompletedAt() != null && export.getSizeBytes() > 0) {
            return;
        }

        String tempDir = exportProperties.getTempDir();
        if (!StringUtils.hasText(tempDir)) {
            fail(message, "INVALID_ARGUMENT", "export tempDir is blank");
            return;
        }

        Path taskDir = null;
        try {
            taskDir = prepareTaskDir(tempDir, message.taskId());
            Path outputFile = taskDir.resolve(export.getFileName());
            Instant startedAt = Instant.now();
            RuntimeLimiter limiter = new RuntimeLimiter(exportProperties.getLimitsOrDefault(message.tier()), startedAt, outputFile, message.tier());

            Locale locale = ExportWorkerUtils.parseLocale(message.locale());
            ZoneId zoneId = ExportWorkerUtils.parseZoneId(message.timeZone());

            List<ExportFieldDefinition> allFields = exportSchemaRegistry.fields(message.exportType());
            List<ExportFieldDefinition> selected = selectFields(allFields, message.fields());

            update(message, MessageCenterConstants.TASK_STATUS_RUNNING, stagePayload(export, "QUERYING"));

            long rowCount = exportByType(message, export, selected, locale, zoneId, outputFile, limiter);

            long sizeBytes = Files.size(outputFile);
            limiter.checkSizeOrThrow(sizeBytes, rowCount);
            validateQuotaOrThrow(message.userId(), message.tier(), sizeBytes);

            update(message, MessageCenterConstants.TASK_STATUS_RUNNING, stagePayload(export, "UPLOADING"));
            putObject(export.getS3Key(), export.getContentType(), outputFile);

            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            export.setSizeBytes(sizeBytes);
            export.setRowCount(rowCount);
            export.setCompletedAt(now);
            exportFileRepositoryJpa.save(export);

            userStorageUsageFacade.incrementUsage(message.userId(), StorageSourceType.EXPORT, StorageFileType.DOCUMENT, 1, sizeBytes);

            Map<String, Object> payload = stagePayload(export, "SUCCESS");
            payload.put("completedAt", now.toString());
            payload.put("sizeBytes", sizeBytes);
            payload.put("rowCount", rowCount);
            payload.put("output", Map.of(
                    "exportId", export.getId().toString(),
                    "fileName", export.getFileName(),
                    "sizeBytes", sizeBytes
            ));

            update(message, MessageCenterConstants.TASK_STATUS_SUCCESS, payload);
        } catch (ExportLimitExceededException ex) {
            fail(message, ex.code, ex.displayMessage);
        } catch (StorageQuotaExceededException ex) {
            fail(message, "STORAGE_QUOTA_EXCEEDED", "storage quota exceeded");
        } catch (Exception ex) {
            log.error("Export worker failed: taskId={}, exportId={}", message.taskId(), message.exportId(), ex);
            fail(message, "INTERNAL_ERROR", safeMessage(ex));
        } finally {
            cleanup(taskDir);
        }
    }

    private long exportByType(
            ExportTaskPendingMessage message,
            ExportFileEntity export,
            List<ExportFieldDefinition> selected,
            Locale locale,
            ZoneId zoneId,
            Path outputFile,
            RuntimeLimiter limiter) throws Exception {

        return switch (message.exportType()) {
            case DEVICE_LOGS -> exportDeviceLogs(message, selected, locale, zoneId, outputFile, limiter);
            case COMMAND_LOGS -> exportCommandLogs(message, selected, locale, zoneId, outputFile, limiter);
            case DEVICE_ONLINE_SESSIONS -> exportOnlineSessions(message, selected, locale, zoneId, outputFile, limiter);
            case PROGRAM_PLAY_SESSIONS -> exportProgramPlaySessions(message, selected, locale, zoneId, outputFile, limiter);
            case MEDIA_PLAY_SESSIONS -> exportMediaPlaySessions(message, selected, locale, zoneId, outputFile, limiter);
        };
    }

    private long exportDeviceLogs(
            ExportTaskPendingMessage message,
            List<ExportFieldDefinition> selected,
            Locale locale,
            ZoneId zoneId,
            Path outputFile,
            RuntimeLimiter limiter) throws Exception {

        JsonNode filters = message.filters();
        OffsetDateTime from = ExportWorkerUtils.requireOffsetDateTime(filters, "from");
        OffsetDateTime to = ExportWorkerUtils.requireOffsetDateTime(filters, "to");
        Long deviceId = ExportWorkerUtils.longOrNull(filters, "deviceId");
        List<Integer> operationIds = ExportWorkerUtils.intList(filters, "operationIds");

        final DeviceLogsExportRepository.Cursor[] cursor = {null};

        return switch (message.format()) {
            case CSV -> writeCsv(outputFile, selected, locale, zoneId, limiter, () -> {
                List<DeviceLogsExportRepository.Row> batch = deviceLogsExportRepository.list(
                        message.userId(), from, to, deviceId, operationIds, cursor[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursor[0] = new DeviceLogsExportRepository.Cursor(last.createdAt(), last.id());
                }
                return batch;
            }, row -> toTextRow(message.exportType(), row, selected, zoneId));
            case JSON -> writeJson(outputFile, selected, zoneId, limiter, () -> {
                List<DeviceLogsExportRepository.Row> batch = deviceLogsExportRepository.list(
                        message.userId(), from, to, deviceId, operationIds, cursor[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursor[0] = new DeviceLogsExportRepository.Cursor(last.createdAt(), last.id());
                }
                return batch;
            }, row -> toJsonObject(row, selected, zoneId, message.exportType()));
            case XLSX -> writeXlsx(outputFile, selected, locale, zoneId, limiter, () -> {
                List<DeviceLogsExportRepository.Row> batch = deviceLogsExportRepository.list(
                        message.userId(), from, to, deviceId, operationIds, cursor[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursor[0] = new DeviceLogsExportRepository.Cursor(last.createdAt(), last.id());
                }
                return batch;
            }, row -> toExcelRow(message.exportType(), row, selected, zoneId));
            case PARQUET -> writeParquet(outputFile, selected, limiter, () -> {
                List<DeviceLogsExportRepository.Row> batch = deviceLogsExportRepository.list(
                        message.userId(), from, to, deviceId, operationIds, cursor[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursor[0] = new DeviceLogsExportRepository.Cursor(last.createdAt(), last.id());
                }
                return batch;
            }, row -> toParquetRow(message.exportType(), row, selected, zoneId));
        };
    }

    private long exportCommandLogs(
            ExportTaskPendingMessage message,
            List<ExportFieldDefinition> selected,
            Locale locale,
            ZoneId zoneId,
            Path outputFile,
            RuntimeLimiter limiter) throws Exception {

        JsonNode filters = message.filters();
        OffsetDateTime from = ExportWorkerUtils.requireOffsetDateTime(filters, "from");
        OffsetDateTime to = ExportWorkerUtils.requireOffsetDateTime(filters, "to");
        Long deviceId = ExportWorkerUtils.longOrNull(filters, "deviceId");
        UUID operationId = ExportWorkerUtils.uuidOrNull(filters, "operationId");
        List<String> actionTypes = ExportWorkerUtils.stringList(filters, "actionTypes");
        List<String> statuses = ExportWorkerUtils.stringList(filters, "statuses");
        Boolean accepted = ExportWorkerUtils.boolOrNull(filters, "accepted");
        Boolean covered = ExportWorkerUtils.boolOrNull(filters, "covered");
        Integer queuedId = ExportWorkerUtils.intOrNull(filters, "queuedId");
        String sendMethod = ExportWorkerUtils.textOrNull(filters, "sendMethod");

        final CommandLogsExportRepository.Cursor[] cursor = {null};

        return switch (message.format()) {
            case CSV -> writeCsv(outputFile, selected, locale, zoneId, limiter, () -> {
                List<CommandLogsExportRepository.Row> batch = commandLogsExportRepository.list(
                        message.userId(), from, to, deviceId, operationId, actionTypes, statuses,
                        accepted, covered, queuedId, sendMethod, cursor[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursor[0] = new CommandLogsExportRepository.Cursor(last.createdAt(), last.id());
                }
                return batch;
            }, row -> toTextRow(message.exportType(), row, selected, zoneId));
            case JSON -> writeJson(outputFile, selected, zoneId, limiter, () -> {
                List<CommandLogsExportRepository.Row> batch = commandLogsExportRepository.list(
                        message.userId(), from, to, deviceId, operationId, actionTypes, statuses,
                        accepted, covered, queuedId, sendMethod, cursor[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursor[0] = new CommandLogsExportRepository.Cursor(last.createdAt(), last.id());
                }
                return batch;
            }, row -> toJsonObject(row, selected, zoneId, message.exportType()));
            case XLSX -> writeXlsx(outputFile, selected, locale, zoneId, limiter, () -> {
                List<CommandLogsExportRepository.Row> batch = commandLogsExportRepository.list(
                        message.userId(), from, to, deviceId, operationId, actionTypes, statuses,
                        accepted, covered, queuedId, sendMethod, cursor[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursor[0] = new CommandLogsExportRepository.Cursor(last.createdAt(), last.id());
                }
                return batch;
            }, row -> toExcelRow(message.exportType(), row, selected, zoneId));
            case PARQUET -> writeParquet(outputFile, selected, limiter, () -> {
                List<CommandLogsExportRepository.Row> batch = commandLogsExportRepository.list(
                        message.userId(), from, to, deviceId, operationId, actionTypes, statuses,
                        accepted, covered, queuedId, sendMethod, cursor[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursor[0] = new CommandLogsExportRepository.Cursor(last.createdAt(), last.id());
                }
                return batch;
            }, row -> toParquetRow(message.exportType(), row, selected, zoneId));
        };
    }

    private long exportOnlineSessions(
            ExportTaskPendingMessage message,
            List<ExportFieldDefinition> selected,
            Locale locale,
            ZoneId zoneId,
            Path outputFile,
            RuntimeLimiter limiter) throws Exception {

        JsonNode filters = message.filters();
        Long deviceId = ExportWorkerUtils.requireLong(filters, "deviceId");
        Instant from = ExportWorkerUtils.requireInstant(filters, "from");
        Instant to = ExportWorkerUtils.requireInstant(filters, "to");

        String resolvedDeviceName = null;
        try {
            var device = deviceRepository.findByDeviceIdAndUserId(deviceId, message.userId());
            resolvedDeviceName = device != null ? device.getDeviceName() : null;
        } catch (Exception ignore) {
        }
        final String deviceName = resolvedDeviceName;

        final Instant[] cursor = {null};
        return switch (message.format()) {
            case CSV -> writeCsv(outputFile, selected, locale, zoneId, limiter, () -> {
                List<DeviceOnlineSessionItem> batch = deviceOnlineTimeFacade.listDeviceSessions(
                        message.userId(), deviceId, from, to, DEFAULT_BATCH_SIZE, cursor[0]);
                if (batch != null && !batch.isEmpty()) {
                    cursor[0] = batch.getLast().onlineAt();
                }
                return batch == null ? List.of() : batch.stream().map(i -> new OnlineSessionRow(deviceId, deviceName, i)).toList();
            }, row -> toTextRow(message.exportType(), row, selected, zoneId));
            case JSON -> writeJson(outputFile, selected, zoneId, limiter, () -> {
                List<DeviceOnlineSessionItem> batch = deviceOnlineTimeFacade.listDeviceSessions(
                        message.userId(), deviceId, from, to, DEFAULT_BATCH_SIZE, cursor[0]);
                if (batch != null && !batch.isEmpty()) {
                    cursor[0] = batch.getLast().onlineAt();
                }
                return batch == null ? List.of() : batch.stream().map(i -> new OnlineSessionRow(deviceId, deviceName, i)).toList();
            }, row -> toJsonObject(row, selected, zoneId, message.exportType()));
            case XLSX -> writeXlsx(outputFile, selected, locale, zoneId, limiter, () -> {
                List<DeviceOnlineSessionItem> batch = deviceOnlineTimeFacade.listDeviceSessions(
                        message.userId(), deviceId, from, to, DEFAULT_BATCH_SIZE, cursor[0]);
                if (batch != null && !batch.isEmpty()) {
                    cursor[0] = batch.getLast().onlineAt();
                }
                return batch == null ? List.of() : batch.stream().map(i -> new OnlineSessionRow(deviceId, deviceName, i)).toList();
            }, row -> toExcelRow(message.exportType(), row, selected, zoneId));
            case PARQUET -> writeParquet(outputFile, selected, limiter, () -> {
                List<DeviceOnlineSessionItem> batch = deviceOnlineTimeFacade.listDeviceSessions(
                        message.userId(), deviceId, from, to, DEFAULT_BATCH_SIZE, cursor[0]);
                if (batch != null && !batch.isEmpty()) {
                    cursor[0] = batch.getLast().onlineAt();
                }
                return batch == null ? List.of() : batch.stream().map(i -> new OnlineSessionRow(deviceId, deviceName, i)).toList();
            }, row -> toParquetRow(message.exportType(), row, selected, zoneId));
        };
    }

    private long exportProgramPlaySessions(
            ExportTaskPendingMessage message,
            List<ExportFieldDefinition> selected,
            Locale locale,
            ZoneId zoneId,
            Path outputFile,
            RuntimeLimiter limiter) throws Exception {

        JsonNode filters = message.filters();
        OffsetDateTime from = ExportWorkerUtils.requireOffsetDateTime(filters, "from");
        OffsetDateTime to = ExportWorkerUtils.requireOffsetDateTime(filters, "to");

        String lanProgramId = ExportWorkerUtils.textOrNull(filters, "lanProgramId");
        UUID programId = ExportWorkerUtils.uuidOrNull(filters, "programId");
        Integer releaseVersion = ExportWorkerUtils.intOrNull(filters, "releaseVersion");

        final OffsetDateTime[] cursorStartAt = {null};
        final Long[] cursorId = {null};

        BatchSupplier<ProgramPlaySessionItem> fetcher;
        if (StringUtils.hasText(lanProgramId)) {
            fetcher = () -> {
                List<ProgramPlaySessionItem> batch = playbackTelemetryFacade.listProgramPlaySessionsForLan(
                        message.userId(), lanProgramId.trim(), from, to, cursorStartAt[0], cursorId[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursorStartAt[0] = last.startAt();
                    cursorId[0] = last.id();
                }
                return batch;
            };
        } else {
            if (programId == null || releaseVersion == null) {
                throw new IllegalArgumentException("programId and releaseVersion are required");
            }
            int version = releaseVersion;
            fetcher = () -> {
                List<ProgramPlaySessionItem> batch = playbackTelemetryFacade.listProgramPlaySessionsForPlatform(
                        message.userId(), programId, version, from, to, cursorStartAt[0], cursorId[0], DEFAULT_BATCH_SIZE);
                if (!batch.isEmpty()) {
                    var last = batch.getLast();
                    cursorStartAt[0] = last.startAt();
                    cursorId[0] = last.id();
                }
                return batch;
            };
        }

        return switch (message.format()) {
            case CSV -> writeCsv(outputFile, selected, locale, zoneId, limiter, fetcher,
                    row -> toTextRow(message.exportType(), row, selected, zoneId));
            case JSON -> writeJson(outputFile, selected, zoneId, limiter, fetcher,
                    row -> toJsonObject(row, selected, zoneId, message.exportType()));
            case XLSX -> writeXlsx(outputFile, selected, locale, zoneId, limiter, fetcher,
                    row -> toExcelRow(message.exportType(), row, selected, zoneId));
            case PARQUET -> writeParquet(outputFile, selected, limiter, fetcher,
                    row -> toParquetRow(message.exportType(), row, selected, zoneId));
        };
    }

    private long exportMediaPlaySessions(
            ExportTaskPendingMessage message,
            List<ExportFieldDefinition> selected,
            Locale locale,
            ZoneId zoneId,
            Path outputFile,
            RuntimeLimiter limiter) throws Exception {

        JsonNode filters = message.filters();
        String mediaId = ExportWorkerUtils.textOrThrow(filters, "mediaId");
        OffsetDateTime from = ExportWorkerUtils.requireOffsetDateTime(filters, "from");
        OffsetDateTime to = ExportWorkerUtils.requireOffsetDateTime(filters, "to");

        final OffsetDateTime[] cursorStartAt = {null};
        final Long[] cursorId = {null};

        BatchSupplier<MediaPlaySessionItem> fetcher = () -> {
            List<MediaPlaySessionItem> batch = playbackTelemetryFacade.listMediaPlaySessions(
                    message.userId(), mediaId, from, to, cursorStartAt[0], cursorId[0], DEFAULT_BATCH_SIZE);
            if (!batch.isEmpty()) {
                var last = batch.getLast();
                cursorStartAt[0] = last.startAt();
                cursorId[0] = last.id();
            }
            return batch;
        };

        return switch (message.format()) {
            case CSV -> writeCsv(outputFile, selected, locale, zoneId, limiter, fetcher,
                    row -> toTextRow(message.exportType(), row, selected, zoneId));
            case JSON -> writeJson(outputFile, selected, zoneId, limiter, fetcher,
                    row -> toJsonObject(row, selected, zoneId, message.exportType()));
            case XLSX -> writeXlsx(outputFile, selected, locale, zoneId, limiter, fetcher,
                    row -> toExcelRow(message.exportType(), row, selected, zoneId));
            case PARQUET -> writeParquet(outputFile, selected, limiter, fetcher,
                    row -> toParquetRow(message.exportType(), row, selected, zoneId));
        };
    }

    record OnlineSessionRow(Long deviceId, String deviceName, DeviceOnlineSessionItem item) {
    }

    @FunctionalInterface
    private interface BatchSupplier<T> {
        List<T> get() throws Exception;
    }

    @FunctionalInterface
    private interface RowMapper<T, R> {
        R map(T row) throws Exception;
    }

    private <T> long writeCsv(
            Path outputFile,
            List<ExportFieldDefinition> selected,
            Locale locale,
            ZoneId zoneId,
            RuntimeLimiter limiter,
            BatchSupplier<T> fetcher,
            RowMapper<T, List<String>> mapper) throws Exception {

        try (OutputStream out = Files.newOutputStream(outputFile)) {
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

                printer.printRecord(headers(selected, locale));

                long rowCount = 0;
                while (true) {
                    List<T> batch = fetcher.get();
                    if (batch == null || batch.isEmpty()) {
                        break;
                    }
                    for (T row : batch) {
                        printer.printRecord(mapper.map(row));
                        rowCount++;
                        if (limiter != null) {
                            limiter.checkRowCountOrThrow(rowCount);
                        }
                    }
                    printer.flush();
                    if (limiter != null) {
                        limiter.checkPeriodicOrThrow(rowCount);
                    }
                }
                if (limiter != null) {
                    limiter.checkPeriodicOrThrow(rowCount);
                }
                return rowCount;
            }
        }
    }

    private <T> long writeJson(
            Path outputFile,
            List<ExportFieldDefinition> selected,
            ZoneId zoneId,
            RuntimeLimiter limiter,
            BatchSupplier<T> fetcher,
            RowMapper<T, Map<String, Object>> mapper) throws Exception {

        try (OutputStream out = Files.newOutputStream(outputFile);
             JsonGenerator gen = new JsonFactory().createGenerator(out)) {
            gen.writeStartArray();
            long rowCount = 0;
            while (true) {
                List<T> batch = fetcher.get();
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (T row : batch) {
                    Map<String, Object> obj = mapper.map(row);
                    gen.writeStartObject();
                    for (var field : selected) {
                        gen.writeFieldName(field.key());
                        ExportWorkerUtils.writeJsonValue(gen, obj.get(field.key()));
                    }
                    gen.writeEndObject();
                    rowCount++;
                    if (limiter != null) {
                        limiter.checkRowCountOrThrow(rowCount);
                    }
                }
                gen.flush();
                if (limiter != null) {
                    limiter.checkPeriodicOrThrow(rowCount);
                }
            }
            gen.writeEndArray();
            gen.flush();
            if (limiter != null) {
                limiter.checkPeriodicOrThrow(rowCount);
            }
            return rowCount;
        }
    }

    private <T> long writeXlsx(
            Path outputFile,
            List<ExportFieldDefinition> selected,
            Locale locale,
            ZoneId zoneId,
            RuntimeLimiter limiter,
            BatchSupplier<T> fetcher,
            RowMapper<T, List<Object>> mapper) throws Exception {

        List<List<String>> head = selected.stream()
                .map(f -> List.of(messageSource.getMessage(f.headerI18nKey(), null, f.key(), locale)))
                .toList();

        List<nan.produced.prism.core.export.api.dto.ExportValueType> typesByColumn = selected.stream()
                .map(ExportFieldDefinition::valueType)
                .toList();

        ExcelWriter excelWriter = EasyExcel.write(outputFile.toFile())
                .head(head)
                .registerWriteHandler(new ExportExcelCellStyleHandler(typesByColumn))
                .build();

        WriteSheet sheet = EasyExcel.writerSheet(0, "Export").build();

        long rowCount = 0;
        try {
            while (true) {
                List<T> batch = fetcher.get();
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                List<List<Object>> rows = new ArrayList<>(batch.size());
                for (T row : batch) {
                    rows.add(mapper.map(row));
                    rowCount++;
                    if (limiter != null) {
                        limiter.checkRowCountOrThrow(rowCount);
                    }
                }
                excelWriter.write(rows, sheet);
                if (limiter != null) {
                    limiter.checkPeriodicOrThrow(rowCount);
                }
            }
        } finally {
            excelWriter.finish();
        }

        if (limiter != null) {
            limiter.checkPeriodicOrThrow(rowCount);
        }
        return rowCount;
    }

    private <T> long writeParquet(
            Path outputFile,
            List<ExportFieldDefinition> selected,
            RuntimeLimiter limiter,
            BatchSupplier<T> fetcher,
            RowMapper<T, Map<String, Object>> mapper) throws Exception {

        Schema schema = buildAvroSchema(selected);
        OutputFile out = new LocalParquetOutputFile(outputFile);

        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(out)
                .withSchema(schema)
                .withCompressionCodec(CompressionCodecName.SNAPPY)
                .build()) {

            long rowCount = 0;
            while (true) {
                List<T> batch = fetcher.get();
                if (batch == null || batch.isEmpty()) {
                    break;
                }
                for (T row : batch) {
                    Map<String, Object> map = mapper.map(row);
                    GenericRecord record = new GenericData.Record(schema);
                    for (var field : selected) {
                        Object v = map.get(field.key());
                        record.put(field.key(), toParquetValue(v, field.valueType()));
                    }
                    writer.write(record);
                    rowCount++;
                    if (limiter != null) {
                        limiter.checkRowCountOrThrow(rowCount);
                    }
                }
                if (limiter != null) {
                    limiter.checkPeriodicOrThrow(rowCount);
                }
            }
            if (limiter != null) {
                limiter.checkPeriodicOrThrow(rowCount);
            }
            return rowCount;
        }
    }

    private List<String> headers(List<ExportFieldDefinition> selected, Locale locale) {
        List<String> headers = new ArrayList<>(selected.size());
        for (var f : selected) {
            headers.add(messageSource.getMessage(f.headerI18nKey(), null, f.key(), locale));
        }
        return headers;
    }

    private List<Object> toExcelRow(ExportType type, Object row, List<ExportFieldDefinition> selected, ZoneId zoneId) {
        List<Object> values = new ArrayList<>(selected.size());
        for (var field : selected) {
            Object v = extractValue(type, row, field.key(), field.valueType(), zoneId);
            values.add(v);
        }
        return values;
    }

    private List<String> toTextRow(ExportType type, Object row, List<ExportFieldDefinition> selected, ZoneId zoneId) {
        List<String> values = new ArrayList<>(selected.size());
        for (var field : selected) {
            Object v = extractValue(type, row, field.key(), field.valueType(), zoneId);
            values.add(ExportWorkerUtils.formatText(v, field.valueType()));
        }
        return values;
    }

    private Map<String, Object> toJsonObject(Object row, List<ExportFieldDefinition> selected, ZoneId zoneId, ExportType type) {
        Map<String, Object> obj = new HashMap<>();
        for (var field : selected) {
            Object v = extractValue(type, row, field.key(), field.valueType(), zoneId);
            obj.put(field.key(), v);
        }
        return obj;
    }

    private Map<String, Object> toParquetRow(ExportType type, Object row, List<ExportFieldDefinition> selected, ZoneId zoneId) {
        Map<String, Object> obj = new HashMap<>();
        for (var field : selected) {
            Object v = extractValue(type, row, field.key(), field.valueType(), zoneId);
            obj.put(field.key(), v);
        }
        return obj;
    }

    private Schema buildAvroSchema(List<ExportFieldDefinition> selected) {
        SchemaBuilder.FieldAssembler<Schema> fields = SchemaBuilder.record("ExportRow")
                .namespace("nan.produced.prism.core.export")
                .fields();
        for (var f : selected) {
            Schema valueSchema = switch (f.valueType()) {
                case BOOLEAN -> Schema.create(Schema.Type.BOOLEAN);
                case INT -> Schema.create(Schema.Type.INT);
                case LONG -> Schema.create(Schema.Type.LONG);
                case DOUBLE -> Schema.create(Schema.Type.DOUBLE);
                case DATETIME -> LogicalTypes.timestampMillis().addToSchema(Schema.create(Schema.Type.LONG));
                case JSON, TEXT -> Schema.create(Schema.Type.STRING);
            };
            Schema union = Schema.createUnion(List.of(Schema.create(Schema.Type.NULL), valueSchema));
            fields = fields.name(f.key()).type(union).noDefault();
        }
        return fields.endRecord();
    }

    private Object toParquetValue(Object v, nan.produced.prism.core.export.api.dto.ExportValueType valueType) {
        if (v == null) {
            return null;
        }
        if (valueType == nan.produced.prism.core.export.api.dto.ExportValueType.DATETIME && v instanceof java.util.Date d) {
            return d.getTime();
        }
        return v;
    }

    private Object extractValue(ExportType type, Object row, String key, nan.produced.prism.core.export.api.dto.ExportValueType valueType, ZoneId zoneId) {
        if (type == null || row == null || !StringUtils.hasText(key)) {
            return null;
        }
        return switch (type) {
            case DEVICE_LOGS -> ExportWorkerUtils.extractDeviceLog((DeviceLogsExportRepository.Row) row, key, valueType, zoneId);
            case COMMAND_LOGS -> ExportWorkerUtils.extractCommandLog((CommandLogsExportRepository.Row) row, key, valueType, zoneId);
            case DEVICE_ONLINE_SESSIONS -> ExportWorkerUtils.extractOnlineSession((OnlineSessionRow) row, key, valueType, zoneId);
            case PROGRAM_PLAY_SESSIONS -> ExportWorkerUtils.extractProgramPlaySession((ProgramPlaySessionItem) row, key, valueType, zoneId);
            case MEDIA_PLAY_SESSIONS -> ExportWorkerUtils.extractMediaPlaySession((MediaPlaySessionItem) row, key, valueType, zoneId);
        };
    }

    private List<ExportFieldDefinition> selectFields(List<ExportFieldDefinition> allFields, List<String> keys) {
        if (allFields == null || allFields.isEmpty()) {
            return List.of();
        }
        if (keys == null || keys.isEmpty()) {
            return allFields;
        }
        Map<String, ExportFieldDefinition> byKey = new HashMap<>();
        for (var f : allFields) {
            byKey.put(f.key(), f);
        }
        List<ExportFieldDefinition> selected = new ArrayList<>();
        for (String k : keys) {
            if (!StringUtils.hasText(k)) {
                continue;
            }
            ExportFieldDefinition f = byKey.get(k.trim());
            if (f != null) {
                selected.add(f);
            }
        }
        return selected.isEmpty() ? allFields : selected;
    }

    private void validateQuotaOrThrow(UUID userId, String tier, long outputBytes) {
        if (userId == null || outputBytes <= 0) {
            return;
        }
        UserStorageQuotaSnapshot quota = userStorageQuotaQueryFacade.getStorageQuota(userId, tier);
        Long limit = quota != null ? quota.quotaBytes() : null;
        Long available = quota != null ? quota.availableBytes() : null;
        if (limit == null || limit < 0) {
            return;
        }
        if (available == null) {
            return;
        }
        if (available < outputBytes) {
            throw new StorageQuotaExceededException();
        }
    }

    private void putObject(String objectKey, String contentType, Path file) {
        objectStoragePort.putObject(objectKey, file, contentType);
    }

    private Path prepareTaskDir(String baseDir, String taskId) throws Exception {
        Path root = Path.of(baseDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path dir = root.resolve(taskId);
        Files.createDirectories(dir);
        return dir;
    }

    private void cleanup(Path dir) {
        if (dir == null) {
            return;
        }
        try {
            if (!Files.exists(dir)) {
                return;
            }
            try (var stream = Files.walk(dir)) {
                stream.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignore) {
                            }
                        });
            }
        } catch (Exception ignore) {
        }
    }

    private Map<String, Object> stagePayload(ExportFileEntity export, String stage) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("taskType", "EXPORT");
        payload.put("taskId", export.getTaskId());
        payload.put("exportId", export.getId().toString());
        payload.put("exportType", export.getExportType().name());
        payload.put("format", export.getFormat().name());
        payload.put("stage", stage);
        payload.put("fileName", export.getFileName());
        return payload;
    }

    private void update(ExportTaskPendingMessage message, String status, Object payload) {
        messageCenterFacade.updateTaskMessage(message.userId(), message.messageId(), status, payload);
    }

    private void fail(ExportTaskPendingMessage message, String code, String error) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("taskType", "EXPORT");
            payload.put("taskId", message.taskId());
            payload.put("exportId", message.exportId().toString());
            payload.put("exportType", message.exportType() != null ? message.exportType().name() : null);
            payload.put("format", message.format() != null ? message.format().name() : null);
            payload.put("stage", "FAILED");
            payload.put("error", Map.of("code", code, "message", error));
            update(message, MessageCenterConstants.TASK_STATUS_FAILED, payload);
        } catch (Exception ex) {
            log.warn("Export worker - fail update failed (ignored): taskId={}", message.taskId(), ex);
        }
    }

    private String safeMessage(Exception ex) {
        return ex != null && ex.getMessage() != null ? ex.getMessage() : "export failed";
    }

    private static final class StorageQuotaExceededException extends RuntimeException {
    }

    private static final class ExportLimitExceededException extends RuntimeException {
        private final String code;
        private final String displayMessage;

        private ExportLimitExceededException(String code, String displayMessage) {
            this.code = code;
            this.displayMessage = displayMessage;
        }
    }

    private static final class RuntimeLimiter {
        private final TierLimits limits;
        private final Instant startedAt;
        private final Path outputFile;
        private final String tier;

        private RuntimeLimiter(TierLimits limits, Instant startedAt, Path outputFile, String tier) {
            this.limits = limits;
            this.startedAt = startedAt != null ? startedAt : Instant.now();
            this.outputFile = outputFile;
            this.tier = tier;
        }

        void checkRowCountOrThrow(long rowCount) {
            if (limits == null) {
                return;
            }
            long maxRows = limits.getMaxRows();
            if (maxRows > 0 && rowCount > maxRows) {
                throw new ExportLimitExceededException("LIMIT_EXCEEDED", buildMessage("row limit exceeded, maxRows=" + maxRows));
            }
        }

        void checkPeriodicOrThrow(long rowCount) {
            if (limits == null) {
                return;
            }
            int maxRunSeconds = limits.getMaxRunSeconds();
            if (maxRunSeconds > 0) {
                long elapsedSeconds = Duration.between(startedAt, Instant.now()).getSeconds();
                if (elapsedSeconds > maxRunSeconds) {
                    throw new ExportLimitExceededException("LIMIT_EXCEEDED", buildMessage("time limit exceeded, maxRunSeconds=" + maxRunSeconds));
                }
            }
            long maxBytes = limits.getMaxBytes();
            if (maxBytes > 0 && outputFile != null) {
                try {
                    if (Files.exists(outputFile) && Files.size(outputFile) > maxBytes) {
                        throw new ExportLimitExceededException("LIMIT_EXCEEDED", buildMessage("file too large, maxBytes=" + maxBytes));
                    }
                } catch (ExportLimitExceededException ex) {
                    throw ex;
                } catch (Exception ignore) {
                }
            }
        }

        void checkSizeOrThrow(long sizeBytes, long rowCount) {
            if (limits == null) {
                return;
            }
            long maxBytes = limits.getMaxBytes();
            if (maxBytes > 0 && sizeBytes > maxBytes) {
                throw new ExportLimitExceededException("LIMIT_EXCEEDED", buildMessage("file too large, maxBytes=" + maxBytes));
            }
            checkRowCountOrThrow(rowCount);
            checkPeriodicOrThrow(rowCount);
        }

        private String buildMessage(String detail) {
            if (tier != null && "FREE".equalsIgnoreCase(tier.trim())) {
                return detail + " (Free tier limit, upgrade to Pro for larger exports)";
            }
            return detail;
        }
    }
}
