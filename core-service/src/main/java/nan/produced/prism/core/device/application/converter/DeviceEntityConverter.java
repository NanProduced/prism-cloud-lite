package nan.produced.prism.core.device.application.converter;

import java.time.OffsetDateTime;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.dto.CreateDeviceDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

/**
 * 设备实体 Mapper（MapStruct）
 * <p>
 * 用于将应用层 DTO 转换为持久化实体，减少手写 builder/set 代码。
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceEntityConverter {

    @Mapping(target = "deviceId", source = "deviceId")
    @Mapping(target = "deviceName", source = "dto.displayName")
    @Mapping(target = "description", source = "dto.description")
    @Mapping(target = "userId", source = "dto.userId")
    @Mapping(target = "createTime", source = "now")
    @Mapping(target = "onlineStatus", ignore = true)
    @Mapping(target = "onboardingTime", ignore = true)
    @Mapping(target = "lastReportTime", ignore = true)
    @Mapping(target = "model", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "brightness", ignore = true)
    @Mapping(target = "networkType", ignore = true)
    @Mapping(target = "playingProgram", ignore = true)
    @Mapping(target = "resolution", ignore = true)
    @Mapping(target = "totalStorage", ignore = true)
    @Mapping(target = "freeStorage", ignore = true)
    @Mapping(target = "powerStatus", ignore = true)
    @Mapping(target = "properties", ignore = true)
    DeviceEntity toNewEntity(CreateDeviceDTO dto, Long deviceId, OffsetDateTime now);
}
