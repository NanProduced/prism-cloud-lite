package nan.produced.prism.core.device.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.device.api.dto.commandlog.DeviceCommandLogDetailResp;
import nan.produced.prism.core.device.api.dto.commandlog.DeviceCommandLogListItemResp;
import nan.produced.prism.core.device.api.dto.commandlog.DeviceCommandLogPageResp;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.DeviceCommandLog;
import nan.produced.prism.core.device.domain.command.DeviceCommandStatus;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceCommandLogRepositoryJpa;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceRepositoryJpa;
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
    private static final int MAX_DEVICE_MATCHES = 200;

    private final DeviceCommandLogRepositoryJpa deviceCommandLogRepositoryJpa;
    private final DeviceRepositoryJpa deviceRepositoryJpa;

    public DeviceCommandLogPageResp listCommandLogs(UUID userId,
                                                    OffsetDateTime from,
                                                    OffsetDateTime to,
                                                    Long deviceId,
                                                    String deviceName,
                                                    String operationId,
                                                    List<String> actionTypes,
                                                    List<String> statuses,
                                                    Boolean accepted,
                                                    Boolean covered,
                                                    Integer queuedId,
                                                    String sendMethod,
                                                    int page,
                                                    Integer size) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        if (deviceId != null && StringUtils.hasText(deviceName)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "`deviceId` and `deviceName` cannot be used together");
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

        List<Long> deviceIds = resolveDeviceIdsByName(userId, deviceName);
        if (deviceIds != null && deviceIds.isEmpty()) {
            return new DeviceCommandLogPageResp(List.of(), safePage, safeSize, 0);
        }

        Specification<DeviceCommandLog> spec = buildSpec(
                userId,
                safeFrom,
                safeTo,
                deviceId,
                deviceIds,
                operationId,
                actionTypeEnums,
                statusEnums,
                accepted,
                covered,
                queuedId,
                sendMethod
        );

        var pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        var result = deviceCommandLogRepositoryJpa.findAll(spec, pageable);
        Map<Long, String> deviceNameById = loadDeviceNameById(userId, result.getContent());
        List<DeviceCommandLogListItemResp> items = result.getContent().stream()
                .map(entity -> toListItem(entity, deviceNameById.get(entity.getDeviceId())))
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

        String deviceName = null;
        DeviceEntity device = deviceRepositoryJpa.findByDeviceIdAndUserId(entity.getDeviceId(), userId);
        if (device != null) {
            deviceName = device.getDeviceName();
        }
        return toDetail(entity, deviceName);
    }

    private Specification<DeviceCommandLog> buildSpec(UUID userId,
                                                      OffsetDateTime from,
                                                      OffsetDateTime to,
                                                      Long deviceId,
                                                      List<Long> deviceIds,
                                                      String operationId,
                                                      List<DeviceActionType> actionTypes,
                                                      List<DeviceCommandStatus> statuses,
                                                      Boolean accepted,
                                                      Boolean covered,
                                                      Integer queuedId,
                                                      String sendMethod) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));

            if (deviceIds != null && !deviceIds.isEmpty()) {
                predicates.add(root.get("deviceId").in(deviceIds));
            } else if (deviceId != null) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId));
            }
            if (StringUtils.hasText(operationId)) {
                UUID operationUuid;
                try {
                    operationUuid = UUID.fromString(operationId.trim());
                } catch (IllegalArgumentException ex) {
                    throw new InfraException(ErrorCode.INVALID_REQUEST, "invalid operationId");
                }
                predicates.add(cb.equal(root.get("operationId"), operationUuid));
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

    private DeviceCommandLogListItemResp toListItem(DeviceCommandLog entity, String deviceName) {
        return new DeviceCommandLogListItemResp(
                entity.getId(),
                entity.getDeviceId(),
                deviceName,
                entity.getOperationId() != null ? entity.getOperationId().toString() : null,
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

    private DeviceCommandLogDetailResp toDetail(DeviceCommandLog entity, String deviceName) {
        return new DeviceCommandLogDetailResp(
                entity.getId(),
                entity.getDeviceId(),
                deviceName,
                entity.getOperationId() != null ? entity.getOperationId().toString() : null,
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

    private Map<Long, String> loadDeviceNameById(UUID userId, Collection<DeviceCommandLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Map.of();
        }
        List<Long> deviceIds = logs.stream().map(DeviceCommandLog::getDeviceId).distinct().toList();
        return deviceRepositoryJpa.findByUserIdAndDeviceIdIn(userId, deviceIds).stream()
            .collect(java.util.stream.Collectors.toMap(DeviceEntity::getDeviceId, DeviceEntity::getDeviceName));
    }
}
