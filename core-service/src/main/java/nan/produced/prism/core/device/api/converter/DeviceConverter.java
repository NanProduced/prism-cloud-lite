package nan.produced.prism.core.device.api.converter;

import nan.produced.prism.core.device.api.dto.CreateDeviceReq;
import nan.produced.prism.core.device.domain.dto.CreateDeviceDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

import java.util.UUID;

/**
 * 设备实体转换
 *
 * @author Nan
 */
@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceConverter {


    /**
     * 将创建设备请求转换为创建设备数据传输对象
     *
     * @param userId  用户ID
     * @param request 创建设备请求对象
     * @return CreateDeviceDTO 创建设备数据传输对象
     */
    @Mapping(source = "userId", target = "userId")
    @Mapping(source = "request.displayName", target = "displayName")
    @Mapping(source = "request.account", target = "account")
    @Mapping(source = "request.password", target = "password")
    @Mapping(source = "request.description", target = "description")
    CreateDeviceDTO toCreateDeviceDTO(UUID userId, CreateDeviceReq request);
}