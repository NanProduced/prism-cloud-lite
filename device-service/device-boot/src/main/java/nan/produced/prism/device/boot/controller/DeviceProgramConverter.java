package nan.produced.prism.device.boot.controller;

import nan.produced.prism.device.api.dto.program.DeviceApiProgram;
import nan.produced.prism.device.infrastructure.internal.core.dto.DeviceProgramDTO;
import org.mapstruct.Mapper;
import org.mapstruct.NullValueCheckStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS)
public interface DeviceProgramConverter {

    DeviceApiProgram toDeviceApiProgram(DeviceProgramDTO dto);

    List<DeviceApiProgram> toDeviceApiProgram(List<DeviceProgramDTO> dtos);
}
