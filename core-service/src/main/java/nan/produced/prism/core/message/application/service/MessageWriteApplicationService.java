package nan.produced.prism.core.message.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.messaging.FrontendEventMessage;
import nan.produced.prism.core.common.messaging.MessagingConstants;
import nan.produced.prism.core.common.messaging.RabbitMessagePublisher;
import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.message.api.dto.MessageListItemResp;
import nan.produced.prism.core.message.domain.MessageEntity;
import nan.produced.prism.core.message.domain.MessageKind;
import nan.produced.prism.core.message.domain.MessageStatus;
import nan.produced.prism.core.message.infrastructure.persistence.MessageRepositoryJpa;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MessageWriteApplicationService {

    private static final String SSE_TYPE_MESSAGE_CREATED = "message.created";
    private static final String SSE_TYPE_MESSAGE_UPDATED = "message.updated";

    private final MessageRepositoryJpa messageRepositoryJpa;
    private final RabbitMessagePublisher rabbitMessagePublisher;

    public MessageEntity createAndPublish(MessageKind kind,
                                         String type,
                                         MessageStatus status,
                                         UUID userId,
                                         String title,
                                         String summary,
                                         Object payload,
                                         Long deviceId,
                                         UUID programId,
                                         String operationId,
                                         String taskId) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (kind == null) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "kind is required");
        }
        if (!StringUtils.hasText(type)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "type is required");
        }
        if (status == null) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "status is required");
        }
        if (!StringUtils.hasText(title)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "title is required");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MessageEntity entity = MessageEntity.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .kind(kind)
            .type(type.trim())
            .status(status)
            .title(title.trim())
            .summary(StringUtils.hasText(summary) ? summary.trim() : null)
            .payload(serializePayload(payload))
            .deviceId(deviceId)
            .programId(programId)
            .operationId(StringUtils.hasText(operationId) ? operationId.trim() : null)
            .taskId(StringUtils.hasText(taskId) ? taskId.trim() : null)
            .createdAt(now)
            .updatedAt(now)
            .build();

        messageRepositoryJpa.save(entity);
        publishMessageCreated(entity);
        return entity;
    }

    public MessageEntity updateStatusAndPublish(UUID userId, UUID messageId, MessageStatus status, Object payloadPatch) {
        return updateAndPublish(userId, messageId, status, null, null, payloadPatch);
    }

    public MessageEntity updateAndPublish(UUID userId,
                                         UUID messageId,
                                         MessageStatus status,
                                         String title,
                                         String summary,
                                         Object payloadPatch) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (messageId == null) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "messageId is required");
        }

        MessageEntity entity = messageRepositoryJpa.findByIdAndUserId(messageId, userId)
            .orElseThrow(() -> new InfraException(ErrorCode.MESSAGE_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        entity.setUpdatedAt(now);

        if (status != null) {
            entity.setStatus(status);
        }

        if (StringUtils.hasText(title)) {
            entity.setTitle(title.trim());
        }

        if (summary != null) {
            String normalizedSummary = StringUtils.hasText(summary) ? summary.trim() : null;
            entity.setSummary(normalizedSummary);
        }

        if (payloadPatch != null) {
            entity.setPayload(serializePayload(payloadPatch));
        }

        messageRepositoryJpa.save(entity);
        publishMessageUpdated(entity);
        return entity;
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        String json = JsonUtils.toJson(payload);
        if (!StringUtils.hasText(json)) {
            return null;
        }
        return json;
    }

    private void publishMessageCreated(MessageEntity entity) {
        if (entity == null || entity.getUserId() == null) {
            return;
        }

        MessageListItemResp item = MessageListItemResp.builder()
            .id(entity.getId())
            .kind(entity.getKind())
            .type(entity.getType())
            .status(entity.getStatus())
            .title(entity.getTitle())
            .summary(entity.getSummary())
            .deviceId(entity.getDeviceId())
            .programId(entity.getProgramId())
            .operationId(entity.getOperationId())
            .taskId(entity.getTaskId())
            .createdAt(entity.getCreatedAt())
            .readAt(entity.getReadAt())
            .build();

        Map<String, Object> data = new HashMap<>();
        data.put("message", item);

        FrontendEventMessage message = FrontendEventMessage.builder()
            .success(true)
            .type(SSE_TYPE_MESSAGE_CREATED)
            .scope(FrontendEventMessage.Scope.builder()
                .userId(entity.getUserId())
                .build())
            .data(data)
            .build();

        rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.NOTIFY_MESSAGE_CREATED, message);
    }

    private void publishMessageUpdated(MessageEntity entity) {
        if (entity == null || entity.getUserId() == null) {
            return;
        }

        MessageListItemResp item = MessageListItemResp.builder()
            .id(entity.getId())
            .kind(entity.getKind())
            .type(entity.getType())
            .status(entity.getStatus())
            .title(entity.getTitle())
            .summary(entity.getSummary())
            .deviceId(entity.getDeviceId())
            .programId(entity.getProgramId())
            .operationId(entity.getOperationId())
            .taskId(entity.getTaskId())
            .createdAt(entity.getCreatedAt())
            .readAt(entity.getReadAt())
            .build();

        Map<String, Object> data = new HashMap<>();
        data.put("message", item);

        FrontendEventMessage message = FrontendEventMessage.builder()
            .success(true)
            .type(SSE_TYPE_MESSAGE_UPDATED)
            .scope(FrontendEventMessage.Scope.builder()
                .userId(entity.getUserId())
                .build())
            .data(data)
            .build();

        rabbitMessagePublisher.publishCoreNotification(MessagingConstants.RoutingKeys.NOTIFY_MESSAGE_UPDATED, message);
    }
}
