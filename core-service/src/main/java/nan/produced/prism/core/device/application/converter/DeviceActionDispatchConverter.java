package nan.produced.prism.core.device.application.converter;

import nan.produced.prism.core.common.util.JsonUtils;
import nan.produced.prism.core.common.util.VsnFilenameUtils;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.device.api.dto.DeviceActionDispatchResp;
import nan.produced.prism.core.device.api.dto.DeviceActionDispatchStatus;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;
import nan.produced.prism.core.device.domain.command.DeviceActionBodyBase;
import nan.produced.prism.core.device.domain.command.DeviceActionType;
import nan.produced.prism.core.device.domain.command.action.DeleteDeviceVsnAction;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandReq;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandResp;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueCheckStrategy;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceActionDispatchConverter {

    @Mapping(target = "deviceId", source = "deviceId")
    @Mapping(target = "commandId", source = "commandId")
    @Mapping(target = "authorUrl", expression = "java(resolveAuthorUrl(action))")
    @Mapping(target = "karma", expression = "java(action.getType().getKarma())")
    @Mapping(target = "ttlMinutes", source = "action.ttlMinutes")
    @Mapping(target = "content", expression = "java(toContent(action.getBody()))")
    DeviceCommandReq toDeviceCommandReq(Long deviceId, String commandId, DeviceActionBase action);

    @Mapping(target = "operationId", source = "commandId")
    @Mapping(target = "deviceId", source = "deviceId")
    @Mapping(target = "type", source = "action.type")
    @Mapping(target = "trackingLevel", source = "action.type.trackingLevel")
    @Mapping(target = "status", expression = "java(toDispatchStatus(result))")
    @Mapping(target = "accepted", expression = "java(isAccepted(result))")
    @Mapping(target = "sendMethod", source = "result.sendMethod")
    @Mapping(target = "queuedId", source = "result.queuedId")
    @Mapping(target = "covered", source = "result.covered")
    @Mapping(target = "errorMessage", expression = "java(toErrorMessage(result))")
    DeviceActionDispatchResp toDispatchResp(Long deviceId, String commandId, DeviceActionBase action, DeviceCommandResp.CommandResult result);

    default DeviceCommandReq.Content toContent(DeviceActionBodyBase body) {
        if (body == null) {
            return null;
        }
        String raw = JsonUtils.toJson(body);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return DeviceCommandReq.Content.builder().raw(raw).build();
    }

    default boolean isAccepted(DeviceCommandResp.CommandResult result) {
        return result != null && result.isAccepted();
    }

    default DeviceActionDispatchStatus toDispatchStatus(DeviceCommandResp.CommandResult result) {
        return isAccepted(result) ? DeviceActionDispatchStatus.DISPATCHED : DeviceActionDispatchStatus.REJECTED;
    }

    default String toErrorMessage(DeviceCommandResp.CommandResult result) {
        if (result == null) {
            return "device-service 未返回结果";
        }
        return result.getErrorMessage();
    }

    default String resolveAuthorUrl(DeviceActionBase action) {
        if (action == null || action.getType() == null) {
            return null;
        }
        if (action.getType() == DeviceActionType.DELETE_DEVICE_VSN && action instanceof DeleteDeviceVsnAction delete) {
            String source = delete.getSource() != null ? delete.getSource().trim().toLowerCase() : "";
            if (!"internet".equals(source) && !"lan".equals(source)) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "invalid source: " + delete.getSource());
            }

            String rawName = delete.getVsnName() != null ? delete.getVsnName().trim() : "";
            int lastSlash = Math.max(rawName.lastIndexOf('/'), rawName.lastIndexOf('\\'));
            String vsnName = lastSlash >= 0 && lastSlash + 1 < rawName.length() ? rawName.substring(lastSlash + 1) : rawName;
            if (!vsnName.toLowerCase().endsWith(".vsn")) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "invalid vsnName (missing .vsn): " + delete.getVsnName());
            }
            if (VsnFilenameUtils.parseVsnMeta(vsnName) == null) {
                throw new BizException(ErrorCode.INVALID_REQUEST, "invalid vsnName: " + delete.getVsnName());
            }

            return "api/vsns/sources/" + source + "/vsns/" + vsnName;
        }
        return action.getType().getUrl();
    }
}
