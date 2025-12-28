package nan.produced.prism.core.device.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.device.api.dto.commandlog.DeviceCommandLogDetailResp;
import nan.produced.prism.core.device.api.dto.commandlog.DeviceCommandLogListItemResp;
import nan.produced.prism.core.device.api.dto.commandlog.DeviceCommandLogPageResp;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceCommandLogRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class DeviceCommandLogApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int RETENTION_DAYS = 90;

    private final DeviceCommandLogRepositoryJpa deviceCommandLogRepositoryJpa;

    public DeviceCommandLogPageResp listCommandLogs(UUID userId,
                                                    OffsetDateTime from,
                                                    OffsetDateTime to,
                                                    Long deviceId,
                                                    String operationId,
                                                    List<String> actionTypes,
                                                    List<String> statuses,
                                                    Boolean accepted,
                                                    Boolean covered,
                                                    Integer queuedId,
                                                    String sendMethod,
                                                    String keyword,
                                                    int page,
                                                    Integer size) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        int safePage = Math.max(0, page);
        int safeSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cutoff = now.minusDays(RETENTION_DAYS);

        OffsetDateTime safeTo = to != null ? to : now;
        if (safeTo.isAfter(now)) {
            safeTo = now;
        }

        OffsetDateTime safeFrom = from != null ? from : safeTo.minusDays(7);
        if (safeFrom.isBefore(cutoff)) {
            safeFrom = cutoff;
        }

        if (safeTo.isBefore(safeFrom)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "`to` must be after `from`");
        }

        List<DeviceActionType> actionTypeEnums = parseActionTypes(actionTypes);
        List<DeviceCommandStatus> statusEnums = parseStatuses(statuses);

        Specification<DeviceCommandLog> spec = buildSpec(
                userId,
                safeFrom,
                safeTo,
                deviceId,
                operationId,
                actionTypeEnums,
                statusEnums,
                accepted,
                covered,
                queuedId,
                sendMethod,
                keyword
        );

        var pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        var result = deviceCommandLogRepositoryJpa.findAll(spec, pageable);
        List<DeviceCommandLogListItemResp> items = result.getContent().stream()
                .map(this::toListItem)
                .toList();

        return new DeviceCommandLogPageResp(items, safePage, safeSize, result.getTotalElements());
    }

    public DeviceCommandLogDetailResp getCommandLog(UUID userId, Long logId) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (logId == null) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "logId is required");
        }

        DeviceCommandLog entity = deviceCommandLogRepositoryJpa.findByIdAndUserId(logId, userId)
                .orElseThrow(() -> new InfraException(ErrorCode.DEVICE_COMMAND_LOG_NOT_FOUND));

        return toDetail(entity);
    }

    private Specification<DeviceCommandLog> buildSpec(UUID userId,
                                                      OffsetDateTime from,
                                                      OffsetDateTime to,
                                                      Long deviceId,
                                                      String operationId,
                                                      List<DeviceActionType> actionTypes,
                                                      List<DeviceCommandStatus> statuses,
                                                      Boolean accepted,
                                                      Boolean covered,
                                                      Integer queuedId,
                                                      String sendMethod,
                                                      String keyword) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));

            if (deviceId != null) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId));
            }
            if (StringUtils.hasText(operationId)) {
                predicates.add(cb.equal(root.get("operationId"), operationId.trim()));
            }
            if (actionTypes != null && !actionTypes.isEmpty()) {
                predicates.add(root.get("actionType").in(actionTypes));
            }
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (accepted != null) {
                predicates.add(cb.equal(root.get("accepted"), accepted));
            }
            if (covered != null) {
                predicates.add(cb.equal(root.get("covered"), covered));
            }
            if (queuedId != null) {
                predicates.add(cb.equal(root.get("queuedId"), queuedId));
            }
            if (StringUtils.hasText(sendMethod)) {
                predicates.add(cb.equal(cb.lower(root.get("sendMethod")), sendMethod.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                var errorMessage = cb.coalesce(root.get("errorMessage"), "");
                var payload = cb.coalesce(root.get("payload"), "");
                predicates.add(cb.or(
                        cb.like(cb.lower(errorMessage), like),
                        cb.like(cb.lower(payload), like),
                        cb.like(cb.lower(root.get("operationId")), like)
                ));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private List<DeviceActionType> parseActionTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<DeviceActionType> list = new ArrayList<>();
        for (String value : raw) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                list.add(DeviceActionType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new InfraException(ErrorCode.INVALID_REQUEST, "invalid actionTypes");
            }
        }
        return list;
    }

    private List<DeviceCommandStatus> parseStatuses(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<DeviceCommandStatus> list = new ArrayList<>();
        for (String value : raw) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            try {
                list.add(DeviceCommandStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new InfraException(ErrorCode.INVALID_REQUEST, "invalid statuses");
            }
        }
        return list;
    }

    private DeviceCommandLogListItemResp toListItem(DeviceCommandLog entity) {
        return new DeviceCommandLogListItemResp(
                entity.getId(),
                entity.getDeviceId(),
                entity.getOperationId(),
                entity.getActionType() != null ? entity.getActionType().name() : null,
                entity.getTrackingLevel() != null ? entity.getTrackingLevel().name() : null,
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.isAccepted(),
                entity.isCovered(),
                entity.getSendMethod(),
                entity.getQueuedId(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DeviceCommandLogDetailResp toDetail(DeviceCommandLog entity) {
        return new DeviceCommandLogDetailResp(
                entity.getId(),
                entity.getDeviceId(),
                entity.getOperationId(),
                entity.getActionType() != null ? entity.getActionType().name() : null,
                entity.getTrackingLevel() != null ? entity.getTrackingLevel().name() : null,
                entity.getStatus() != null ? entity.getStatus().name() : null,
                entity.getPayload(),
                entity.getTtlMinutes(),
                entity.getSendMethod(),
                entity.getQueuedId(),
                entity.isAccepted(),
                entity.isCovered(),
                entity.getErrorMessage(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

