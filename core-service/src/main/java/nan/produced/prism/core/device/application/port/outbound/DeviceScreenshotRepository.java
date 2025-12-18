package nan.produced.prism.core.device.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.screenshot.DeviceScreenshotEntity;

public interface DeviceScreenshotRepository {

    DeviceScreenshotEntity save(DeviceScreenshotEntity entity);

    Optional<DeviceScreenshotEntity> findByScreenshotId(UUID screenshotId);

    Optional<DeviceScreenshotEntity> findByScreenshotIdAndDeviceId(UUID screenshotId, Long deviceId);

    Optional<DeviceScreenshotEntity> findLatestByDeviceId(Long deviceId);

    List<DeviceScreenshotEntity> findLatestByDeviceIds(List<Long> deviceIds);

    List<DeviceScreenshotEntity> findByDeviceIdOrderByUploadedAtDesc(Long deviceId);

    List<DeviceScreenshotEntity> findByDeviceIdInOrderByUploadedAtDesc(List<Long> deviceIds);

    void delete(DeviceScreenshotEntity entity);

    void deleteAll(Iterable<DeviceScreenshotEntity> entities);
}
