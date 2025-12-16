package nan.produced.prism.core.device.application.service;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.exception.InfraException;
import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.device.application.mapper.DeviceEntityMapper;
import nan.produced.prism.core.device.application.port.inbound.DeviceManageUseCase;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import nan.produced.prism.core.device.domain.dto.CreateDeviceDTO;
import nan.produced.prism.core.integration.device.client.DeviceInternalClient;
import nan.produced.prism.core.user.api.UserQuotaFacade;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DeviceManageApplicationService implements DeviceManageUseCase {

    private final DeviceInternalClient deviceInternalClient;

    private final DeviceRepository deviceRepository;

    private final DeviceEntityMapper deviceEntityMapper;

    private final UserQuotaFacade userQuotaFacade;

    @Override
    @Transactional
    public Long createDevice(CreateDeviceDTO createDeviceDTO) {

        if (createDeviceDTO == null) {
            throw new InfraException(ErrorCode.INTERNAL_SERVER_ERROR, "createDeviceDTO is null");
        }

        userQuotaFacade.consumeDevices(createDeviceDTO.getUserId(), createDeviceDTO.getTier(), 1);

        ResponseEntity<ApiResponse<Long>> response;
        try {
            response = deviceInternalClient.createDevice(createDeviceDTO.getAccount(), createDeviceDTO.getPassword());
        } catch (Exception ex) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "调用 device-service 创建设备账号失败", ex);
        }

        if (response == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回 null ResponseEntity");
        }
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service HTTP 状态异常: " + response.getStatusCode());
        }

        ApiResponse<Long> body = response.getBody();
        if (body == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回空响应体");
        }
        if (!isDeviceServiceSuccessCode(body.getCode())) {
            throw new InfraException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "device-service 返回失败: code=" + body.getCode() + ", message=" + body.getMessage());
        }
        Long deviceId = body.getData();
        if (deviceId == null) {
            throw new InfraException(ErrorCode.EXTERNAL_SERVICE_ERROR, "device-service 返回空 deviceId");
        }

        DeviceEntity newDevice = deviceEntityMapper.toNewEntity(createDeviceDTO, deviceId, LocalDateTime.now());
        deviceRepository.createDevice(newDevice);
        return deviceId;
    }

    private boolean isDeviceServiceSuccessCode(String code) {
        return "200".equals(code) || ErrorCode.SUCCESS.getCode().equals(code);
    }
}
