package nan.produced.prism.core.device.application.converter;

import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldDefEntity;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldOptionEntity;
import nan.produced.prism.core.device.domain.dto.DeviceCustomFieldDefVO;
import nan.produced.prism.core.device.domain.dto.DeviceCustomFieldOptionVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

/**
 * 设备自定义列 Mapper（MapStruct）
 * <p>
 * 注意：DefVO.options 的排序/聚合由应用服务控制（避免引入隐式行为变化）。
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceCustomFieldConverter {

    @Mapping(target = "options", ignore = true)
    DeviceCustomFieldDefVO toDefVO(DeviceCustomFieldDefEntity entity);

    DeviceCustomFieldOptionVO toOptionVO(DeviceCustomFieldOptionEntity entity);
}

