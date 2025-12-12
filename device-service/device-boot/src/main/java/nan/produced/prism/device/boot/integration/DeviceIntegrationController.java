package nan.produced.prism.device.boot.integration;


import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.application.domain.device.DeviceAccount;
import nan.produced.prism.device.application.port.inbound.auth.DeviceAccountUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/device")
@RequiredArgsConstructor
public class DeviceIntegrationController {

    private final DeviceAccountUseCase deviceAccountUseCase;

    /**
     * 创建设备账号
     * @param username 账户名
     * @param password 密码
     * @return 设备ID
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Long>> createDevice(@RequestParam("username") String username,
                                                          @RequestParam("password") String password) {

        DeviceAccount deviceAccount = deviceAccountUseCase.createDeviceAccount(username, password);

        return ResponseEntity.ok(ApiResponse.success(deviceAccount.getDeviceId()));
    }
}
