package nan.produced.prism.core.device.application.converter;

import nan.produced.prism.core.device.domain.report.log.DeviceLog;
import nan.produced.prism.core.device.domain.report.log.DeviceLogEntity;
import nan.produced.prism.core.device.domain.report.log.DeviceLogType;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceLogConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "deviceId", source = "deviceId")
    @Mapping(target = "operationId", ignore = true)
    @Mapping(target = "deviceTimeRaw", source = "log.deviceTime")
    @Mapping(target = "handleTimeRaw", source = "log.handleTime")
    @Mapping(target = "reportTime", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    DeviceLogEntity convert(DeviceLog log, Long deviceId);

    default List<DeviceLogEntity> convert(List<DeviceLog> logs, Long deviceId) {
        if (logs == null || logs.isEmpty()) {
            return List.of();
        }

        // 手动循环调用上面的单个转换方法
        List<DeviceLogEntity> list = new java.util.ArrayList<>(logs.size());
        for (DeviceLog log : logs) {
            if (log == null) {
                continue;
            }
            DeviceLogEntity entity = convert(log, deviceId);
            if (entity != null) {
                list.add(entity);
            }
        }
        return list;
    }

    /**
     * 填充设备日志操作类型
     * @param deviceLog 设备日志
     * @param entity 设备日志实体
     */
    @AfterMapping
    default void fillDeviceLogOperation(DeviceLog deviceLog, @MappingTarget DeviceLogEntity entity, Long deviceId) {
        entity.setOperationId(DeviceLogType.getTypeId(deviceLog));
    }
}
