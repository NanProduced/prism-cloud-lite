package nan.produced.prism.device.application.port.inbound.auth;

import nan.produced.prism.device.application.domain.device.DeviceAccount;

/**
 * 终端账号用例接口
 *
 * @author Nan
 */
public interface DeviceAccountUseCase {

    /**
     * 创建终端账号
     *
     * @param account 账号
     * @param password 密码
     * @return 终端账号
     */
    DeviceAccount createDeviceAccount(String account, String password);
}
