package nan.produced.prism.core.integration.device.client;

import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandReq;
import nan.produced.prism.core.integration.device.dto.command.DeviceCommandResp;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "device-service", configuration = ServiceSignatureFeignConfig.class, path = "/internal/device")
public interface DeviceInternalClient {

    @PostMapping("/create")
    ResponseEntity<ApiResponse<Long>> createDevice(
            @RequestParam("username") String username,
            @RequestParam("password") String password);

    /**
     * 统一指令下发入口（批量）
     */
    @PostMapping("/command")
    ResponseEntity<ApiResponse<DeviceCommandResp>> sendCommand(
            @RequestBody List<DeviceCommandReq> request);

}
