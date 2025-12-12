package nan.produced.prism.core.device.application.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.device.application.port.inbound.DeviceManageUseCase;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.dto.CreateDeviceDTO;
import nan.produced.prism.core.integration.device.client.DeviceInternalClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceManageApplicationService implements DeviceManageUseCase {

    private final DeviceInternalClient deviceInternalClient;

    private final DeviceRepository deviceRepository;

    @Override
    public Long createDevice(CreateDeviceDTO createDeviceDTO) {

        //todo: 检查quota

        ResponseEntity<ApiResponse<Long>> response = deviceInternalClient.createDevice(createDeviceDTO.getAccount(), createDeviceDTO.getPassword());
        Long deviceId = response.getBody().getData();
        DeviceEntity newDevice = DeviceEntity.builder()
                .deviceId(deviceId)
                .deviceName(createDeviceDTO.getDisplayName())
                .description(createDeviceDTO.getDescription())
                .userId(createDeviceDTO.getUserId())
                .createTime(LocalDateTime.now())
                .build();
        deviceRepository.createDevice(newDevice);
        return deviceId;
    }
}
