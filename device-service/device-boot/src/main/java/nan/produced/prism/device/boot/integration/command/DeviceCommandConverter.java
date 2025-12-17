package nan.produced.prism.device.boot.integration.command;

import nan.produced.prism.device.api.dto.comand.DeviceApiCommand;
import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.application.dto.command.DeviceCommandResultDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

import java.time.LocalDateTime;
import java.util.List;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceCommandConverter {

    /**
     * 将请求对象转换为实体对象
     */
    @Mapping(source = "content.raw", target = "contentRaw")
    @Mapping(source = "ttlMinutes", target = "expireTime", qualifiedByName = "calculateExpireTime")
    @Mapping(target = "queueId", ignore = true)
    @Mapping(target = "cacheTime", ignore = true)
    DeviceCommand toDeviceCommand(DeviceCommandReq req);

    List<DeviceCommand> toDeviceCommand(List<DeviceCommandReq> req);

    DeviceCommandResp.CommandResult toCommandResult(DeviceCommandResultDTO dto);

    List<DeviceCommandResp.CommandResult> toCommandResult(List<DeviceCommandResultDTO> dtos);

    @Mapping(source = "queueId", target = "id")
    @Mapping(source = "deviceId", target = "post", qualifiedByName = "longToInteger")
    @Mapping(source = "contentRaw", target = "content", qualifiedByName = "stringToContent")
    DeviceApiCommand toDeviceApiCommand(DeviceCommand command);

    List<DeviceApiCommand> toDeviceApiCommand(List<DeviceCommand> commands);

    /**
     * Long类型deviceId转换为Integer类型post
     */
    @Named("longToInteger")
    default Integer longToInteger(Long value) {
        return value != null ? value.intValue() : null;
    }

    /**
     * 字符串contentRaw转换为Content对象
     */
    @Named("stringToContent")
    default DeviceApiCommand.Content stringToContent(String raw) {
        return raw != null ? new DeviceApiCommand.Content(raw) : null;
    }

    /**
     * 自定义转换逻辑：计算过期时间
     * 如果 ttlMinutes 为空，这里设置了一个默认值（例如 30分钟），你可以根据业务修改
     */
    @Named("calculateExpireTime")
    default LocalDateTime calculateExpireTime(Integer ttlMinutes) {
        // 默认过期时间：如果请求未传，默认 60 分钟后过期
        long minutes = (ttlMinutes != null) ? ttlMinutes : 60L;
        return LocalDateTime.now().plusMinutes(minutes);
    }

}