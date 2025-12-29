package nan.produced.prism.core.message.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceRepositoryJpa;
import nan.produced.prism.core.message.api.dto.MessageDetailResp;
import nan.produced.prism.core.message.api.dto.MessageListItemResp;
import nan.produced.prism.core.message.api.dto.MessagePageResp;
import nan.produced.prism.core.message.domain.MessageEntity;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;
import nan.produced.prism.core.message.infrastructure.config.MessageCenterProperties;
import nan.produced.prism.core.message.infrastructure.persistence.MessageRepositoryJpa;
import nan.produced.prism.core.program.domain.ProgramEntity;
import nan.produced.prism.core.program.infrastructure.persistence.ProgramRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageCenterApplicationService {

    private static final int MAX_DEVICE_MATCHES = 200;
    private static final int MAX_PROGRAM_MATCHES = 200;

    private final MessageRepositoryJpa messageRepositoryJpa;
    private final MessageCenterProperties messageCenterProperties;
    private final DeviceRepositoryJpa deviceRepositoryJpa;
    private final ProgramRepositoryJpa programRepositoryJpa;

    public List<MessageListItemResp> listRecent(UUID userId, MessageKind kind, Integer limit) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        int maxRecentLimit = Math.max(1, messageCenterProperties.getQueryDefaults().getMaxRecentLimit());
        int defaultRecentLimit = Math.min(maxRecentLimit, Math.max(1, messageCenterProperties.getQueryDefaults().getDefaultRecentLimit()));
        int safeLimit = limit == null ? defaultRecentLimit : Math.min(maxRecentLimit, Math.max(1, limit));

        Specification<MessageEntity> spec = buildSpec(
            userId,
            kind,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);

        var pageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<MessageEntity> content = messageRepositoryJpa.findAll(spec, pageable).getContent();
        Map<Long, String> deviceNameById = loadDeviceNameById(userId, content);
        Map<UUID, String> programNameById = loadProgramNameById(userId, content);
        return content.stream()
            .map(entity -> toListItem(entity, deviceNameById.get(entity.getDeviceId()), programNameById.get(entity.getProgramId())))
            .toList();
    }

    public MessagePageResp listMessages(UUID userId,
                                       MessageKind kind,
                                       String type,
                                       MessageStatus status,
                                       String read,
                                       OffsetDateTime from,
                                       OffsetDateTime to,
                                       Long deviceId,
                                       String deviceName,
                                       UUID programId,
                                       String programName,
                                       String operationId,
                                       String taskId,
                                        int page,
                                        Integer size) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        if (deviceId != null && StringUtils.hasText(deviceName)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "`deviceId` and `deviceName` cannot be used together");
        }
        if (programId != null && StringUtils.hasText(programName)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "`programId` and `programName` cannot be used together");
        }

        int safePage = Math.max(0, page);
        int maxPageSize = Math.max(1, messageCenterProperties.getQueryDefaults().getMaxPageSize());
        int defaultPageSize = Math.min(maxPageSize, Math.max(1, messageCenterProperties.getQueryDefaults().getDefaultPageSize()));
        int safeSize = size == null ? defaultPageSize : Math.min(maxPageSize, Math.max(1, size));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int retentionDays = Math.max(1, messageCenterProperties.getRetention().getDays());
        OffsetDateTime cutoff = now.minusDays(retentionDays);

        OffsetDateTime safeTo = to != null ? to : now;
        if (safeTo.isAfter(now)) {
            safeTo = now;
        }

        int defaultFromDays = Math.max(1, messageCenterProperties.getQueryDefaults().getDefaultFromDays());
        OffsetDateTime safeFrom = from != null ? from : safeTo.minusDays(defaultFromDays);
        if (safeFrom.isBefore(cutoff)) {
            safeFrom = cutoff;
        }

        if (safeTo.isBefore(safeFrom)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "`to` must be after `from`");
        }

        String normalizedRead = normalizeReadFilter(read);

        List<Long> deviceIds = resolveDeviceIdsByName(userId, deviceName);
        if (deviceIds != null && deviceIds.isEmpty()) {
            return new MessagePageResp(List.of(), safePage, safeSize, 0);
        }
        List<UUID> programIds = resolveProgramIdsByName(userId, programName);
        if (programIds != null && programIds.isEmpty()) {
            return new MessagePageResp(List.of(), safePage, safeSize, 0);
        }

        Specification<MessageEntity> spec = buildSpec(
            userId,
            kind,
            type,
            status,
            normalizedRead,
            safeFrom,
            safeTo,
            deviceId,
            deviceIds,
            programId,
            programIds,
            operationId,
            taskId);

        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = messageRepositoryJpa.findAll(spec, pageable);

        Map<Long, String> deviceNameById = loadDeviceNameById(userId, result.getContent());
        Map<UUID, String> programNameById = loadProgramNameById(userId, result.getContent());
        List<MessageListItemResp> items = result.getContent().stream()
            .map(entity -> toListItem(entity, deviceNameById.get(entity.getDeviceId()), programNameById.get(entity.getProgramId())))
            .toList();

        return new MessagePageResp(items, safePage, safeSize, result.getTotalElements());
    }

    public MessageDetailResp getMessage(UUID userId, UUID messageId) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (messageId == null) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "messageId is required");
        }

        MessageEntity entity = messageRepositoryJpa.findByIdAndUserId(messageId, userId)
            .orElseThrow(() -> new InfraException(ErrorCode.MESSAGE_NOT_FOUND));

        JsonNode payload = null;
        if (StringUtils.hasText(entity.getPayload())) {
            payload = JsonUtils.fromJson(entity.getPayload());
        }

        String deviceName = null;
        if (entity.getDeviceId() != null) {
            DeviceEntity device = deviceRepositoryJpa.findByDeviceIdAndUserId(entity.getDeviceId(), userId);
            if (device != null) {
                deviceName = device.getDeviceName();
            }
        }

        String programName = null;
        if (entity.getProgramId() != null) {
            ProgramEntity program = programRepositoryJpa.findByIdAndUserId(entity.getProgramId(), userId).orElse(null);
            if (program != null) {
                programName = program.getName();
            }
        }

        return new MessageDetailResp(
            entity.getId(),
            entity.getKind(),
            entity.getType(),
            entity.getStatus(),
            entity.getTitle(),
            entity.getSummary(),
            payload,
            entity.getDeviceId(),
            deviceName,
            entity.getProgramId(),
            programName,
            entity.getOperationId(),
            entity.getTaskId(),
            entity.getReadAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public int markRead(UUID userId, Collection<UUID> ids) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return messageRepositoryJpa.markRead(userId, ids, now);
    }

    public long countUnread(UUID userId) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        return messageRepositoryJpa.countByUserIdAndReadAtIsNull(userId);
    }

    private String normalizeReadFilter(String read) {
        if (!StringUtils.hasText(read)) {
            return null;
        }
        String value = read.trim().toLowerCase();
        return switch (value) {
            case "unread", "read", "all" -> value;
            default -> throw new InfraException(ErrorCode.INVALID_REQUEST, "invalid read filter: " + read);
        };
    }

    private Specification<MessageEntity> buildSpec(UUID userId,
                                                   MessageKind kind,
                                                   String type,
                                                   MessageStatus status,
                                                   String read,
                                                   OffsetDateTime from,
                                                   OffsetDateTime to,
                                                   Long deviceId,
                                                   List<Long> deviceIds,
                                                   UUID programId,
                                                   List<UUID> programIds,
                                                   String operationId,
                                                   String taskId) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            if (kind != null) {
                predicates.add(cb.equal(root.get("kind"), kind));
            }
            if (StringUtils.hasText(type)) {
                predicates.add(cb.equal(root.get("type"), type.trim()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if ("unread".equals(read)) {
                predicates.add(cb.isNull(root.get("readAt")));
            } else if ("read".equals(read)) {
                predicates.add(cb.isNotNull(root.get("readAt")));
            }

            if (deviceIds != null && !deviceIds.isEmpty()) {
                predicates.add(root.get("deviceId").in(deviceIds));
            } else if (deviceId != null) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId));
            }
            if (programIds != null && !programIds.isEmpty()) {
                predicates.add(root.get("programId").in(programIds));
            } else if (programId != null) {
                predicates.add(cb.equal(root.get("programId"), programId));
            }
            if (StringUtils.hasText(operationId)) {
                predicates.add(cb.equal(root.get("operationId"), operationId.trim()));
            }
            if (StringUtils.hasText(taskId)) {
                predicates.add(cb.equal(root.get("taskId"), taskId.trim()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private MessageListItemResp toListItem(MessageEntity entity, String deviceName, String programName) {
        return MessageListItemResp.builder()
            .id(entity.getId())
            .kind(entity.getKind())
            .type(entity.getType())
            .status(entity.getStatus())
            .title(entity.getTitle())
            .summary(entity.getSummary())
            .deviceId(entity.getDeviceId())
            .deviceName(deviceName)
            .programId(entity.getProgramId())
            .programName(programName)
            .operationId(entity.getOperationId())
            .taskId(entity.getTaskId())
            .createdAt(entity.getCreatedAt())
            .readAt(entity.getReadAt())
            .build();
    }

    private List<Long> resolveDeviceIdsByName(UUID userId, String deviceName) {
        if (!StringUtils.hasText(deviceName)) {
            return null;
        }
        String keyword = deviceName.trim();
        var pageable = PageRequest.of(0, MAX_DEVICE_MATCHES + 1, Sort.by(Sort.Direction.DESC, "createTime"));
        List<DeviceEntity> devices = deviceRepositoryJpa.findByUserIdAndDeviceNameLike(userId, keyword, pageable);
        if (devices.size() > MAX_DEVICE_MATCHES) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "too many devices matched by deviceName, please refine");
        }
        return devices.stream().map(DeviceEntity::getDeviceId).toList();
    }

    private List<UUID> resolveProgramIdsByName(UUID userId, String programName) {
        if (!StringUtils.hasText(programName)) {
            return null;
        }
        String keyword = programName.trim();
        var pageable = PageRequest.of(0, MAX_PROGRAM_MATCHES + 1, Sort.by(Sort.Direction.DESC, "updatedAt"));
        List<ProgramEntity> programs = programRepositoryJpa.findByUserIdAndNameLike(userId, keyword, pageable);
        if (programs.size() > MAX_PROGRAM_MATCHES) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "too many programs matched by programName, please refine");
        }
        return programs.stream().map(ProgramEntity::getId).toList();
    }

    private Map<Long, String> loadDeviceNameById(UUID userId, Collection<MessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return Map.of();
        }
        List<Long> deviceIds = messages.stream()
            .map(MessageEntity::getDeviceId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        return deviceRepositoryJpa.findByUserIdAndDeviceIdIn(userId, deviceIds).stream()
            .collect(java.util.stream.Collectors.toMap(DeviceEntity::getDeviceId, DeviceEntity::getDeviceName));
    }

    private Map<UUID, String> loadProgramNameById(UUID userId, Collection<MessageEntity> messages) {
        if (messages == null || messages.isEmpty()) {
            return Map.of();
        }
        List<UUID> programIds = messages.stream()
            .map(MessageEntity::getProgramId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
        if (programIds.isEmpty()) {
            return Map.of();
        }
        return programRepositoryJpa.findByUserIdAndIdIn(userId, programIds).stream()
            .collect(java.util.stream.Collectors.toMap(ProgramEntity::getId, ProgramEntity::getName));
    }
}
