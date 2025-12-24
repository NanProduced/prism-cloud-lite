package nan.produced.prism.core.device.application.service;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.api.DeviceStatusFacade;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeviceStatusApplicationService implements DeviceStatusFacade {

    private final DeviceRepository deviceRepository;

    @Override
    public List<Long> findOnlineDeviceIds(UUID userId, Collection<Long> deviceIds) {
        if (userId == null || deviceIds == null || deviceIds.isEmpty()) {
            return List.of();
        }
        List<DeviceEntity> devices = deviceRepository.findByUserIdAndDeviceIds(userId, deviceIds);
        return devices.stream()
            .filter(d -> d != null && d.getDeviceId() != null && d.getOnlineStatus() != null && d.getOnlineStatus() == 1)
            .map(DeviceEntity::getDeviceId)
            .toList();
    }
}

