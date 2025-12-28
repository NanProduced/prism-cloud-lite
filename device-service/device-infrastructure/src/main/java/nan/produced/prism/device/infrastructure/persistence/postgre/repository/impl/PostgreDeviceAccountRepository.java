package nan.produced.prism.device.infrastructure.persistence.postgre.repository.impl;

import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.application.domain.device.DeviceAccount;
import nan.produced.prism.device.application.domain.device.DeviceAccountStatus;
import nan.produced.prism.device.application.port.outbound.repository.DeviceAccountRepository;
import nan.produced.prism.device.infrastructure.persistence.postgre.entity.DeviceAccountEntity;
import nan.produced.prism.device.infrastructure.persistence.postgre.repository.jpa.DeviceAccountJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PostgreDeviceAccountRepository implements DeviceAccountRepository {

    private final DeviceAccountJpaRepository deviceAccountJpaRepository;

    @Override
    public DeviceAccount findDeviceAccountByName(String accountName) {
        return deviceAccountJpaRepository.findByAccount(accountName)
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    public DeviceAccount findDeviceAccountById(Long deviceId) {
        return deviceAccountJpaRepository.findById(deviceId)
                .map(this::toDomain)
                .orElse(null);
    }

    @Override
    public boolean ifExistDeviceAccount(String accountName) {
        return deviceAccountJpaRepository.existsByAccount(accountName);
    }

    @Override
    @Transactional
    public DeviceAccount save(DeviceAccount terminalAccount) {
        DeviceAccountEntity entity = toEntity(terminalAccount);
        DeviceAccountEntity saved = deviceAccountJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public int updateLoginTimeImmediate(Long deviceId, String clientIp, OffsetDateTime loginTime) {
        return deviceAccountJpaRepository.updateLoginTimeImmediate(deviceId, clientIp, loginTime);
    }

    @Override
    @Transactional
    public int updateLoginTime(Long deviceId, String clientIp, OffsetDateTime loginTime) {
        return deviceAccountJpaRepository.updateLoginTime(deviceId, clientIp, loginTime);
    }

    private DeviceAccountEntity toEntity(DeviceAccount domain) {
        DeviceAccountEntity entity = Optional.ofNullable(domain.getDeviceId())
                .flatMap(deviceAccountJpaRepository::findById)
                .orElseGet(DeviceAccountEntity::new);

        entity.setDeviceId(domain.getDeviceId());
        entity.setAccount(domain.getAccountName());
        entity.setPassword(domain.getPasswordHash());
        entity.setAccountStatus(resolveStatus(domain.getStatus()));
        entity.setFirstLoginTime(domain.getFirstLoginTime());
        entity.setLastLoginTime(domain.getLastLoginTime());
        entity.setLastLoginIp(domain.getLastLoginIp());
        return entity;
    }

    private DeviceAccount toDomain(DeviceAccountEntity entity) {
        return DeviceAccount.builder()
                .deviceId(entity.getDeviceId())
                .accountName(entity.getAccount())
                .passwordHash(entity.getPassword())
                .status(parseStatus(entity.getAccountStatus()))
                .firstLoginTime(entity.getFirstLoginTime())
                .lastLoginTime(entity.getLastLoginTime())
                .lastLoginIp(entity.getLastLoginIp())
                .build();
    }

    private Byte resolveStatus(DeviceAccountStatus status) {
        DeviceAccountStatus resolved = Optional.ofNullable(status)
                .orElse(DeviceAccountStatus.ENABLE);
        return resolved.getStatus().byteValue();
    }

    private DeviceAccountStatus parseStatus(Byte value) {
        if (value == null) {
            return DeviceAccountStatus.ENABLE;
        }
        for (DeviceAccountStatus status : DeviceAccountStatus.values()) {
            if (status.getStatus().intValue() == value.intValue()) {
                return status;
            }
        }
        return DeviceAccountStatus.ENABLE;
    }
}
