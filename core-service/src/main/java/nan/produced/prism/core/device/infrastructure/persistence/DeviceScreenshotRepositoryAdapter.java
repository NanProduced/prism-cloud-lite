package nan.produced.prism.core.device.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.core.device.application.port.outbound.DeviceScreenshotRepository;
import nan.produced.prism.core.device.domain.screenshot.DeviceScreenshotEntity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceScreenshotRepositoryAdapter implements DeviceScreenshotRepository {

    private final DeviceScreenshotRepositoryJpa deviceScreenshotRepositoryJpa;

    @Override
    public DeviceScreenshotEntity save(DeviceScreenshotEntity entity) {
        return deviceScreenshotRepositoryJpa.save(entity);
    }

    @Override
    public Optional<DeviceScreenshotEntity> findByScreenshotId(UUID screenshotId) {
        return deviceScreenshotRepositoryJpa.findByScreenshotId(screenshotId);
    }

    @Override
    public Optional<DeviceScreenshotEntity> findByScreenshotIdAndDeviceId(UUID screenshotId, Long deviceId) {
        return deviceScreenshotRepositoryJpa.findByScreenshotIdAndDeviceId(screenshotId, deviceId);
    }

    @Override
    public Optional<DeviceScreenshotEntity> findLatestByDeviceId(Long deviceId) {
        return deviceScreenshotRepositoryJpa.findFirstByDeviceIdOrderByUploadedAtDesc(deviceId);
    }

    @Override
    public List<DeviceScreenshotEntity> findLatestByDeviceIds(List<Long> deviceIds) {
        return deviceScreenshotRepositoryJpa.findLatestByDeviceIds(deviceIds);
    }

    @Override
    public List<DeviceScreenshotEntity> findByDeviceIdOrderByUploadedAtDesc(Long deviceId) {
        return deviceScreenshotRepositoryJpa.findByDeviceIdOrderByUploadedAtDesc(deviceId);
    }

    @Override
    public List<DeviceScreenshotEntity> findByDeviceIdInOrderByUploadedAtDesc(List<Long> deviceIds) {
        return deviceScreenshotRepositoryJpa.findByDeviceIdInOrderByUploadedAtDesc(deviceIds);
    }

    @Override
    public void delete(DeviceScreenshotEntity entity) {
        deviceScreenshotRepositoryJpa.delete(entity);
    }

    @Override
    public void deleteAll(Iterable<DeviceScreenshotEntity> entities) {
        deviceScreenshotRepositoryJpa.deleteAll(entities);
    }
}
