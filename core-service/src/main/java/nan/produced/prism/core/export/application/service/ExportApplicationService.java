package nan.produced.prism.core.export.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.export.api.ExportFormat;
import nan.produced.prism.core.export.api.ExportType;
import nan.produced.prism.core.export.api.dto.CreateExportRequest;
import nan.produced.prism.core.export.api.dto.CreateExportResponse;
import nan.produced.prism.core.export.api.dto.ExportDownloadResponse;
import nan.produced.prism.core.export.api.dto.ExportFieldDefinition;
import nan.produced.prism.core.export.api.dto.ExportSchemaResponse;
import nan.produced.prism.core.export.api.dto.ExportValueType;
import nan.produced.prism.core.export.domain.ExportFileEntity;
import nan.produced.prism.core.export.infrastructure.config.ExportProperties;
import nan.produced.prism.core.export.infrastructure.config.ExportProperties.TierLimits;
import nan.produced.prism.core.export.infrastructure.messaging.ExportTaskPendingMessage;
import nan.produced.prism.core.export.infrastructure.persistence.ExportFileRepositoryJpa;
import nan.produced.prism.core.media.application.port.outbound.ObjectStoragePort;
import nan.produced.prism.core.message.api.MessageCenterConstants;
import nan.produced.prism.core.message.api.MessageCenterFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageUsageFacade;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportApplicationService {

    private static final String TASK_ID_PREFIX = "export_";
    private static final String TIER_PRO = "PRO";
    private static final int PRIORITY_FREE = 0;
    private static final int PRIORITY_PRO = 9;

    private final ExportProperties exportProperties;
    private final ExportFileRepositoryJpa exportFileRepositoryJpa;
    private final MessageCenterFacade messageCenterFacade;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectStoragePort objectStoragePort;
    private final UserStorageUsageFacade userStorageUsageFacade;
    private final ObjectMapper objectMapper;
    private final ExportSchemaRegistry exportSchemaRegistry;

    public ExportSchemaResponse getSchema(String tier, String exportType) {
        ExportType type = ExportType.parseOrThrow(exportType);

        List<String> allowedFormats = exportProperties.getAllowedFormatsOrDefault(tier).stream()
                .map(Enum::name)
                .toList();

        List<ExportFieldDefinition> fields = exportSchemaRegistry.fields(type);

        List<String> defaultFields = fields.stream().limit(Math.min(8, fields.size())).map(ExportFieldDefinition::key).toList();

        return ExportSchemaResponse.builder()
                .exportType(type.name())
                .allowedFormats(allowedFormats)
                .defaultFields(defaultFields)
                .fields(fields)
                .build();
    }

    @Transactional
    public CreateExportResponse createExportTask(UUID userId, String tier, CreateExportRequest request) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (request == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "request is required");
        }

        ExportType exportType = ExportType.parseOrThrow(request.getExportType());
        ExportFormat format;
        try {
            format = ExportFormat.parseOrThrow(request.getFormat());
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "Unsupported export format: " + request.getFormat());
        }

        String normalizedTier = normalizeTierOrFree(tier);
        if (!isFormatAllowed(normalizedTier, format)) {
            throw new BizException(ErrorCode.EXPORT_PRO_REQUIRED);
        }

        List<String> selectedFields = request.getFields();
        if (selectedFields == null || selectedFields.isEmpty()) {
            selectedFields = getSchema(normalizedTier, exportType.name()).defaultFields();
        }
        selectedFields = selectedFields.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (selectedFields.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "fields is empty");
        }

        TierLimits tierLimits = exportProperties.getLimitsOrDefault(normalizedTier);
        int maxFields = tierLimits != null ? tierLimits.getMaxFields() : 0;
        if (maxFields > 0 && selectedFields.size() > maxFields) {
            if ("FREE".equalsIgnoreCase(normalizedTier)) {
                throw new BizException(ErrorCode.EXPORT_PRO_REQUIRED);
            }
            throw new BizException(ErrorCode.INVALID_REQUEST, "too many fields, max=" + maxFields);
        }

        // 基于 schema 白名单校验字段，避免任意 SQL/任意字段导出
        var schema = getSchema(normalizedTier, exportType.name());
        var allowed = schema.fields().stream().map(ExportFieldDefinition::key).collect(java.util.stream.Collectors.toSet());
        for (String f : selectedFields) {
            if (!allowed.contains(f)) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "unsupported field: " + f);
            }
        }

        JsonNode filters = request.getFilters();
        validateFiltersCheap(normalizedTier, exportType, filters);

        String taskId = TASK_ID_PREFIX + UUID.randomUUID();
        UUID exportId = UUID.randomUUID();

        OffsetDateTime nowUtc = OffsetDateTime.now(ZoneOffset.UTC);
        String ts = nowUtc.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = exportType.name().toLowerCase(Locale.ROOT) + "_" + ts + "." + format.extension();

        String s3Key = buildExportS3Key(normalizedTier, userId, nowUtc, taskId, fileName);

        var payload = new java.util.HashMap<String, Object>();
        payload.put("taskType", "EXPORT");
        payload.put("taskId", taskId);
        payload.put("exportId", exportId.toString());
        payload.put("exportType", exportType.name());
        payload.put("format", format.name());
        payload.put("stage", "PENDING");
        payload.put("createdAt", nowUtc.toString());
        payload.put("fileName", fileName);

        UUID messageId = messageCenterFacade.createTaskMessage(
                userId,
                messageType(exportType),
                payload,
                taskId
        );

        ExportFileEntity entity = ExportFileEntity.builder()
                .id(exportId)
                .userId(userId)
                .messageId(messageId)
                .taskId(taskId)
                .exportType(exportType)
                .format(format)
                .fileName(fileName)
                .contentType(format.contentType())
                .s3Key(s3Key)
                .sizeBytes(0)
                .spec(safeSpecJson(request))
                .build();

        exportFileRepositoryJpa.save(entity);

        enqueue(new ExportTaskPendingMessage(
                taskId,
                messageId,
                exportId,
                userId,
                normalizedTier,
                exportType,
                format,
                request.getLocale(),
                request.getTimeZone(),
                selectedFields,
                filters
        ));

        return CreateExportResponse.builder()
                .taskId(taskId)
                .messageId(messageId)
                .exportId(exportId)
                .build();
    }

    public ExportDownloadResponse getDownloadUrl(UUID userId, UUID exportId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (exportId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "exportId is required");
        }
        ExportFileEntity entity = exportFileRepositoryJpa.findByIdAndUserId(exportId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.EXPORT_FILE_NOT_FOUND));

        if (entity.getDeletedAt() != null) {
            throw new BizException(ErrorCode.EXPORT_FILE_NOT_FOUND);
        }
        if (entity.getCompletedAt() == null || entity.getSizeBytes() <= 0) {
            throw new BizException(ErrorCode.EXPORT_NOT_READY);
        }

        Duration expiration = Duration.ofMinutes(Math.max(1, exportProperties.getDownloadUrlExpirationMinutes()));
        String url = objectStoragePort.generatePresignedGetUrl(
                entity.getS3Key(),
                expiration,
                entity.getContentType(),
                entity.getFileName()
        );

        return ExportDownloadResponse.builder()
                .url(url)
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plus(expiration))
                .build();
    }

    @Transactional
    public void deleteExport(UUID userId, UUID exportId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (exportId == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "exportId is required");
        }
        ExportFileEntity entity = exportFileRepositoryJpa.findByIdAndUserId(exportId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.EXPORT_FILE_NOT_FOUND));

        if (entity.getDeletedAt() != null) {
            return;
        }

        objectStoragePort.deleteObject(entity.getS3Key());

        long bytes = Math.max(0, entity.getSizeBytes());
        if (bytes > 0) {
            userStorageUsageFacade.decrementUsage(userId, StorageSourceType.EXPORT, StorageFileType.DOCUMENT, 1, bytes);
        }

        entity.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        exportFileRepositoryJpa.save(entity);

        // best-effort: 更新任务消息（让 Tasks 里可见“已删除”状态）
        try {
            var payload = new java.util.HashMap<String, Object>();
            payload.put("taskType", "EXPORT");
            payload.put("taskId", entity.getTaskId());
            payload.put("exportId", entity.getId().toString());
            payload.put("exportType", entity.getExportType().name());
            payload.put("format", entity.getFormat().name());
            payload.put("stage", "DELETED");
            payload.put("deletedAt", entity.getDeletedAt().toString());
            messageCenterFacade.updateTaskMessage(userId, entity.getMessageId(), MessageCenterConstants.TASK_STATUS_SUCCESS, payload);
        } catch (Exception ex) {
            log.warn("Export delete - message update failed (ignored): exportId={}", exportId, ex);
        }
    }

    private void enqueue(ExportTaskPendingMessage message) {
        int priority = resolveTaskPriority(message == null ? null : message.tier());
        rabbitTemplate.convertAndSend(
                MessagingConstants.Exchanges.CORE_NOTIFICATIONS,
                MessagingConstants.RoutingKeys.TASK_EXPORT_PENDING,
                message,
                msg -> {
                    msg.getMessageProperties().setPriority(priority);
                    return msg;
                }
        );
    }

    private int resolveTaskPriority(String tier) {
        if (!StringUtils.hasText(tier)) {
            return PRIORITY_FREE;
        }
        return TIER_PRO.equalsIgnoreCase(tier.trim()) ? PRIORITY_PRO : PRIORITY_FREE;
    }

    private boolean isFormatAllowed(String normalizedTier, ExportFormat format) {
        if (format == null) {
            return false;
        }
        return exportProperties.getAllowedFormatsOrDefault(normalizedTier).contains(format);
    }

    private String normalizeTierOrFree(String tier) {
        if (!StringUtils.hasText(tier)) {
            return "FREE";
        }
        String upper = tier.trim().toUpperCase(Locale.ROOT);
        return Objects.equals("PRO", upper) ? "PRO" : "FREE";
    }

    private String messageType(ExportType exportType) {
        return "export." + exportType.name().toLowerCase(Locale.ROOT);
    }

    private String buildExportS3Key(String tier, UUID userId, OffsetDateTime nowUtc, String taskId, String fileName) {
        String root = StringUtils.hasText(exportProperties.getRootPrefix()) ? exportProperties.getRootPrefix().trim() : "export";
        String yyyy = String.format("%04d", nowUtc.getYear());
        String mm = String.format("%02d", nowUtc.getMonthValue());
        String dd = String.format("%02d", nowUtc.getDayOfMonth());
        String safeFile = StringUtils.hasText(fileName) ? fileName.trim() : (taskId + ".bin");
        return String.join("/",
                root,
                tier.toLowerCase(Locale.ROOT),
                userId.toString(),
                yyyy, mm, dd,
                taskId,
                safeFile
        );
    }

    private void validateFiltersCheap(String tier, ExportType exportType, JsonNode filters) {
        // NOTE: 这里仅做 cheap 拦截，worker 侧还会做硬限制兜底（maxRows/maxBytes/maxRunSeconds 等）
        if (exportType == null) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "exportType is required");
        }
        if (filters == null || filters.isNull()) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "filters is required");
        }

        OffsetDateTime from;
        OffsetDateTime to;
        try {
            from = OffsetDateTime.parse(text(filters, "from"));
            to = OffsetDateTime.parse(text(filters, "to"));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "from/to is required");
        }
        if (to.isBefore(from)) {
            throw new BizException(ErrorCode.INVALID_REQUEST, "to must be after from");
        }

        TierLimits tierLimits = exportProperties.getLimitsOrDefault(tier);
        int maxDays = tierLimits != null ? tierLimits.getMaxRangeDays() : 0;
        if (maxDays > 0) {
            Duration range = Duration.between(from, to);
            if (range.compareTo(Duration.ofDays(maxDays)) > 0) {
                if ("FREE".equalsIgnoreCase(tier)) {
                    throw new BizException(ErrorCode.EXPORT_PRO_REQUIRED);
                }
                throw new BizException(ErrorCode.INVALID_REQUEST, "time range too large, maxDays=" + maxDays);
            }
        }

        if ("FREE".equalsIgnoreCase(tier)) {
            switch (exportType) {
                case DEVICE_LOGS, COMMAND_LOGS -> {
                    if (filters.get("deviceId") == null || filters.get("deviceId").isNull()) {
                        throw new BizException(ErrorCode.INVALID_REQUEST, "FREE requires deviceId");
                    }
                }
                case MEDIA_PLAY_SESSIONS -> {
                    if (filters.get("mediaId") == null || filters.get("mediaId").isNull()) {
                        throw new BizException(ErrorCode.INVALID_REQUEST, "FREE requires mediaId");
                    }
                }
                case PROGRAM_PLAY_SESSIONS -> {
                    boolean hasLan = StringUtils.hasText(text(filters, "lanProgramId"));
                    boolean hasPlatform = StringUtils.hasText(text(filters, "programId")) && filters.get("releaseVersion") != null;
                    if (!hasLan && !hasPlatform) {
                        throw new BizException(ErrorCode.INVALID_REQUEST, "FREE requires programId+releaseVersion or lanProgramId");
                    }
                }
                case DEVICE_ONLINE_SESSIONS -> {
                    if (filters.get("deviceId") == null || filters.get("deviceId").isNull()) {
                        throw new BizException(ErrorCode.INVALID_REQUEST, "deviceId is required");
                    }
                }
            }
        }
    }

    private String text(JsonNode node, String key) {
        if (node == null || !StringUtils.hasText(key)) {
            return null;
        }
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return null;
        }
        return v.asText(null);
    }

    private String safeSpecJson(CreateExportRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception ex) {
            return null;
        }
    }
}
