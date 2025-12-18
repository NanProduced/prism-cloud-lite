package nan.produced.prism.device.infrastructure.websocket.processor.v11.converter;

import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.infrastructure.websocket.processor.v11.dto.V11CommandResp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface V11WebsocketDtoConverter {

    @Mapping(source = "queueId", target = "id")
    @Mapping(source = "deviceId", target = "post", qualifiedByName = "longToInteger")
    @Mapping(source = "authorUrl", target = "authorUrl")
    @Mapping(source = "contentRaw", target = "content", qualifiedByName = "stringToContent")
    @Mapping(source = "karma", target = "karma")
    V11CommandResp toV11CommandResp(DeviceCommand deviceCommand);

    List<V11CommandResp> toV11CommandResp(List<DeviceCommand> deviceCommands);

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
    default V11CommandResp.Content stringToContent(String raw) {
        return raw != null ? new V11CommandResp.Content(raw) : null;
    }

}
