package nan.produced.prism.device.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.application.domain.device.DeviceAccount;
import nan.produced.prism.device.application.domain.device.DeviceAccountStatus;
import nan.produced.prism.device.application.port.inbound.auth.DeviceAccountUseCase;
import nan.produced.prism.device.application.port.outbound.auth.EncodePort;
import nan.produced.prism.device.application.port.outbound.repository.DeviceAccountRepository;
import nan.produced.prism.device.common.exception.business.BusinessErrorCode;
import nan.produced.prism.device.common.exception.business.BusinessException;
import org.springframework.stereotype.Service;

/**
 * 终端账号应用服务
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAccountApplicationService implements DeviceAccountUseCase {

    private final DeviceAccountRepository deviceAccountRepository;

    private final EncodePort encodePort;

    @Override
    public DeviceAccount createDeviceAccount(String account, String password) {

        // 1. 检查账号名是否重复
        boolean exist = deviceAccountRepository.ifExistDeviceAccount(account);
        if (exist) {
            throw new BusinessException(BusinessErrorCode.TERMINAL_ACCOUNT_EXIST);
        }

        // 2. 密码加密
        String encodedPassword = encodePort.encodeByPasswordEncoder(password);

        // 3. 创建域对象
        DeviceAccount deviceAccount = DeviceAccount.builder()
                .accountName(account)
                .passwordHash(encodedPassword)
                .status(DeviceAccountStatus.ENABLE)
                .build();

        // 4. 保存到数据库
        DeviceAccount savedAccount = deviceAccountRepository.save(deviceAccount);

        log.info("ApplicationService - 终端账号创建成功, deviceId: {}, accountName: {}",
                savedAccount.getDeviceId(), savedAccount.getAccountName());

        return savedAccount;
    }
}
