package nan.produced.prism.core.device.application.converter;

import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.DeviceNetworkType;
import nan.produced.prism.core.device.domain.DeviceProperties;
import nan.produced.prism.core.device.domain.dto.DeviceListVO;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValueCheckStrategy;

/**
 * 设备视图转换 Mapper（MapStruct）
 * <p>
 * 说明：
 * - 用于设备列表查询场景，将 DeviceEntity 转换为 DeviceListVO；
 * - tags/customFieldValues 等需要额外聚合的数据由应用服务统一补齐。
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceListConverter {

    @Mapping(target = "networkType", source = "networkType")
    @Mapping(target = "networkStrength", ignore = true)
    @Mapping(target = "lastScreenshotUrl", ignore = true)
    @Mapping(target = "tags", ignore = true)
    @Mapping(target = "customFieldValues", ignore = true)
    DeviceListVO toListVO(DeviceEntity entity);

    default DeviceNetworkType toDeviceNetworkType(Integer code) {
        return DeviceNetworkType.getByCode(code);
    }

    @AfterMapping
    default void fillDerivedFields(DeviceEntity entity, @MappingTarget DeviceListVO vo) {
        if (entity == null || vo == null) {
            return;
        }

        // networkStrength：仅 4G 网络返回信号强度（其它类型为 null）
        if (DeviceNetworkType.FOUR_G.equals(vo.getNetworkType())) {
            vo.setNetworkStrength(extract4gStrength(entity.getProperties()));
        } else {
            vo.setNetworkStrength(null);
        }

        // TODO: 设备截图业务尚未实现，先返回 null
        vo.setLastScreenshotUrl(null);
    }

    private static Integer extract4gStrength(DeviceProperties properties) {
        if (properties == null || properties.getIfStatus() == null || properties.getIfStatus().getTypes() == null) {
            return null;
        }
        for (DeviceProperties.IfStatus.NetInterface netInterface : properties.getIfStatus().getTypes()) {
            if (netInterface == null || netInterface.getType() == null) {
                continue;
            }
            if ("4g".equalsIgnoreCase(netInterface.getType())) {
                return netInterface.getStrength();
            }
        }
        return null;
    }
}
