package nan.produced.prism.core.device.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.screenshot.DeviceScreenshotEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceScreenshotRepositoryJpa extends JpaRepository<DeviceScreenshotEntity, UUID> {

    Optional<DeviceScreenshotEntity> findByScreenshotId(UUID screenshotId);

    Optional<DeviceScreenshotEntity> findByScreenshotIdAndDeviceId(UUID screenshotId, Long deviceId);

    Optional<DeviceScreenshotEntity> findFirstByDeviceIdOrderByUploadedAtDesc(Long deviceId);

    @Query(value = """
            SELECT DISTINCT ON (device_id) *
            FROM pcc_device_screenshot
            WHERE device_id IN (:deviceIds)
            ORDER BY device_id, uploaded_at DESC
            """, nativeQuery = true)
    List<DeviceScreenshotEntity> findLatestByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    List<DeviceScreenshotEntity> findByDeviceIdOrderByUploadedAtDesc(Long deviceId);

    List<DeviceScreenshotEntity> findByDeviceIdInOrderByUploadedAtDesc(List<Long> deviceIds);
}
