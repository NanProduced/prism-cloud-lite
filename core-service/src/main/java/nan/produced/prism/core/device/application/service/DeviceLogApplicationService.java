package nan.produced.prism.core.device.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.device.api.dto.log.DeviceLogDetailResp;
import nan.produced.prism.core.device.api.dto.log.DeviceLogListItemResp;
import nan.produced.prism.core.device.api.dto.log.DeviceLogPageResp;
import nan.produced.prism.core.device.api.dto.log.DeviceLogTypeResp;
import nan.produced.prism.core.device.domain.report.log.DeviceLogEntity;
import nan.produced.prism.core.device.domain.report.log.DeviceLogType;
import nan.produced.prism.core.device.infrastructure.persistence.DeviceLogRepositoryJpa;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceLogApplicationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int RETENTION_DAYS = 60;

    private final DeviceLogRepositoryJpa deviceLogRepositoryJpa;

    public DeviceLogPageResp listDeviceLogs(UUID userId,
                                           OffsetDateTime from,
                                           OffsetDateTime to,
                                           Long deviceId,
                                           List<Integer> operationIds,
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

        OffsetDateTime safeFrom = from != null ? from : safeTo.minusDays(1);
        if (safeFrom.isBefore(cutoff)) {
            safeFrom = cutoff;
        }

        if (safeTo.isBefore(safeFrom)) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "`to` must be after `from`");
        }

        if (operationIds != null && !operationIds.isEmpty()) {
            if (!DeviceLogType.isValidaType(new HashSet<>(operationIds))) {
                throw new InfraException(ErrorCode.INVALID_REQUEST, "invalid operationIds");
            }
        }

        Specification<DeviceLogEntity> spec = buildSpec(userId, safeFrom, safeTo, deviceId, operationIds);

        var pageable = PageRequest.of(
            safePage,
            safeSize,
            Sort.by(Sort.Direction.DESC, "createTime").and(Sort.by(Sort.Direction.DESC, "id")));

        var result = deviceLogRepositoryJpa.findAll(spec, pageable);
        List<DeviceLogListItemResp> items = result.getContent().stream()
            .map(this::toListItem)
            .toList();

        return new DeviceLogPageResp(items, safePage, safeSize, result.getTotalElements());
    }

    public DeviceLogDetailResp getDeviceLog(UUID userId, Long logId) {
        if (userId == null) {
            throw new InfraException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (logId == null) {
            throw new InfraException(ErrorCode.INVALID_REQUEST, "logId is required");
        }

        DeviceLogEntity entity = deviceLogRepositoryJpa.findByIdAndUserId(logId, userId)
            .orElseThrow(() -> new InfraException(ErrorCode.DEVICE_LOG_NOT_FOUND));

        return toDetail(entity);
    }

    public List<DeviceLogTypeResp> listDeviceLogTypes() {
        List<DeviceLogTypeResp> list = new ArrayList<>();
        for (DeviceLogType type : DeviceLogType.values()) {
            list.add(new DeviceLogTypeResp(type.getId(), type.getOperation(), type.getType(), type.getSubType()));
        }
        return list;
    }

    private Specification<DeviceLogEntity> buildSpec(UUID userId,
                                                     OffsetDateTime from,
                                                     OffsetDateTime to,
                                                     Long deviceId,
                                                     List<Integer> operationIds) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createTime"), from));
            predicates.add(cb.lessThanOrEqualTo(root.get("createTime"), to));

            if (deviceId != null) {
                predicates.add(cb.equal(root.get("deviceId"), deviceId));
            }

            if (operationIds != null && !operationIds.isEmpty()) {
                predicates.add(root.get("operationId").in(operationIds));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private DeviceLogListItemResp toListItem(DeviceLogEntity entity) {
        return new DeviceLogListItemResp(
            entity.getId(),
            entity.getDeviceId(),
            entity.getOperationId(),
            entity.getLevel(),
            entity.getDescription(),
            entity.getReportTime(),
            entity.getCreateTime()
        );
    }

    private DeviceLogDetailResp toDetail(DeviceLogEntity entity) {
        return new DeviceLogDetailResp(
            entity.getId(),
            entity.getDeviceId(),
            entity.getOperationId(),
            entity.getLevel(),
            entity.getLogType(),
            entity.getSubtype1(),
            entity.getSubtype2(),
            entity.getSubtype3(),
            entity.getCategories(),
            entity.getDescription(),
            entity.getDeviceTimeRaw(),
            entity.getHandleStatus(),
            entity.getHandleTimeRaw(),
            entity.getLogArg1(),
            entity.getLogArg2(),
            entity.getLogArg3(),
            entity.getLogArg4(),
            entity.getLogArg5(),
            entity.getLogArg6(),
            entity.getOthers(),
            entity.getReportTime(),
            entity.getCreateTime()
        );
    }
}
