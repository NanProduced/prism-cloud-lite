package nan.produced.prism.core.device.api.controller;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.device.api.converter.DeviceConverter;
import nan.produced.prism.core.device.api.dto.CreateDeviceReq;
import nan.produced.prism.core.device.api.dto.CreateDeviceResp;
import nan.produced.prism.core.device.domain.dto.CreateDeviceDTO;
import nan.produced.prism.core.security.CloudAuthContext;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/device")
public class DeviceCommonController {

    private final DeviceConverter deviceConverter;

    @PostMapping("/create")
    public BffResponse<CreateDeviceResp> createDevice(@RequestBody @Validated CreateDeviceReq req) {
        String userUuid = CloudAuthContext.getCurrentUser().userUuid();
        UUID userId = UUID.fromString(userUuid);
        CreateDeviceDTO createDeviceDTO = deviceConverter.toCreateDeviceDTO(userId, req);



    }
}
