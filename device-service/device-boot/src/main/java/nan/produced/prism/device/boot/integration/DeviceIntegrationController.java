package nan.produced.prism.device.boot.integration;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.application.domain.command.DeviceCommand;
import nan.produced.prism.device.application.domain.device.DeviceAccount;
import nan.produced.prism.device.application.dto.command.DeviceCommandResultDTO;
import nan.produced.prism.device.application.port.inbound.command.DeviceCommandUseCase;
import nan.produced.prism.device.application.port.inbound.auth.DeviceAccountUseCase;
import nan.produced.prism.device.boot.integration.command.DeviceCommandConverter;
import nan.produced.prism.device.boot.integration.command.DeviceCommandReq;
import nan.produced.prism.device.boot.integration.command.DeviceCommandResp;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/internal/device")
@RequiredArgsConstructor
public class DeviceIntegrationController {

    private final DeviceCommandConverter deviceCommandConverter;
    private final DeviceAccountUseCase deviceAccountUseCase;
    private final DeviceCommandUseCase deviceCommandUseCase;

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

    /**
     * 下发指令（统一入口）
     *
     * <p>覆盖以下场景：</p>
     * <ul>
     *   <li>单设备下发单条指令</li>
     *   <li>多设备下发单条指令（items 中 commandId 不同，内容可相同）</li>
     *   <li>单设备下发多条指令</li>
     *   <li>多设备下发多条指令</li>
     * </ul>
     */
    @PostMapping("/command")
    public ResponseEntity<ApiResponse<DeviceCommandResp>> sendCommand(
            @RequestBody @Validated List<DeviceCommandReq> request) {
        List<DeviceCommand> commands = deviceCommandConverter.toDeviceCommand(request);
        List<DeviceCommandResultDTO> results = deviceCommandUseCase.dispatch(commands);

        int accepted = (int) results.stream().filter(DeviceCommandResultDTO::isAccepted).count();

        DeviceCommandResp resp = DeviceCommandResp.builder()
                .total(results.size())
                .accepted(accepted)
                .results(deviceCommandConverter.toCommandResult(results))
                .build();

        return ResponseEntity.ok(ApiResponse.success(resp));
    }


}
