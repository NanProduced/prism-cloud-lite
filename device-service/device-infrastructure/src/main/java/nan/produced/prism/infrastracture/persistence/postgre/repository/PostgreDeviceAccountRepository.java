package nan.produced.prism.infrastracture.persistence.postgre.repository;

import nan.produced.prism.application.domain.device.DeviceAccount;
import nan.produced.prism.application.port.outbound.repository.DeviceAccountRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class PostgreDeviceAccountRepository implements DeviceAccountRepository {

    @Override
    public DeviceAccount findDeviceAccountByName(String accountName) {
        return null;
    }

    @Override
    public DeviceAccount findDeviceAccountById(Long deviceId) {
        return null;
    }

    @Override
    public boolean ifExistDeviceAccount(String accountName) {
        return false;
    }

    @Override
    public DeviceAccount save(DeviceAccount terminalAccount) {
        return null;
    }

    @Override
    public int updateLoginTimeImmediate(Long deviceId, String clientIp, LocalDateTime loginTime) {
        return 0;
    }

    @Override
    public int updateLoginTime(Long deviceId, String clientIp, LocalDateTime loginTime) {
        return 0;
    }
}
