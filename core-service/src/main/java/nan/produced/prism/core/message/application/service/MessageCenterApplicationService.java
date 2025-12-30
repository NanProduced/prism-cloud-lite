package nan.produced.prism.core.message.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.message.api.dto.MessageDetailResp;
import nan.produced.prism.core.message.api.dto.MessageListItemResp;
import nan.produced.prism.core.message.api.dto.MessagePageResp;
import nan.produced.prism.core.message.domain.MessageEntity;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;
import nan.produced.prism.core.message.infrastructure.config.MessageCenterProperties;
import nan.produced.prism.core.message.infrastructure.persistence.MessageRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageCenterApplicationService {

    private final MessageRepositoryJpa messageRepositoryJpa;
    private final MessageCenterProperties messageCenterProperties;

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
        return content.stream()
            .map(entity -> toListItem(entity, entity.getDeviceNameSnapshot(), entity.getProgramNameSnapshot()))
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

        Specification<MessageEntity> spec = buildSpec(
            userId,
            kind,
            type,
            status,
            normalizedRead,
            safeFrom,
            safeTo,
            deviceId,
            programId,
            deviceName,
            programName,
            operationId,
            taskId);

        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        var result = messageRepositoryJpa.findAll(spec, pageable);

        List<MessageListItemResp> items = result.getContent().stream()
            .map(entity -> toListItem(entity, entity.getDeviceNameSnapshot(), entity.getProgramNameSnapshot()))
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

        String deviceName = entity.getDeviceNameSnapshot();
        String programName = entity.getProgramNameSnapshot();

        return new MessageDetailResp(
            entity.getId(),
            entity.getKind(),
            entity.getType(),
            entity.getStatus(),
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
                                                   UUID programId,
                                                   String deviceName,
                                                   String programName,
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

            if (deviceId != null) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId));
            } else if (StringUtils.hasText(deviceName)) {
                String keyword = deviceName.trim().toLowerCase(Locale.ROOT);
                predicates.add(cb.like(cb.lower(root.get("deviceNameSnapshot")), "%" + keyword + "%"));
            }

            if (programId != null) {
                predicates.add(cb.equal(root.get("programId"), programId));
            } else if (StringUtils.hasText(programName)) {
                String keyword = programName.trim().toLowerCase(Locale.ROOT);
                predicates.add(cb.like(cb.lower(root.get("programNameSnapshot")), "%" + keyword + "%"));
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
        JsonNode payload = null;
        if (StringUtils.hasText(entity.getPayload())) {
            try {
                payload = JsonUtils.fromJson(entity.getPayload());
            } catch (Exception ignore) {
            }
        }

        return MessageListItemResp.builder()
            .id(entity.getId())
            .kind(entity.getKind())
            .type(entity.getType())
            .status(entity.getStatus())
            .payload(payload)
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
}
