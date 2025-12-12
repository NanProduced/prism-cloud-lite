package nan.produced.prism.core.integration.device.client;

import nan.produced.prism.core.common.response.ApiResponse;
import nan.produced.prism.core.integration.signature.ServiceSignatureFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "device-service", configuration = ServiceSignatureFeignConfig.class, path = "/internal/device")
public interface DeviceInternalClient {

    @PostMapping("/create")
    ResponseEntity<ApiResponse<Long>> createDevice(@RequestParam("username") String username,
                                                          @RequestParam("password") String password);

}
