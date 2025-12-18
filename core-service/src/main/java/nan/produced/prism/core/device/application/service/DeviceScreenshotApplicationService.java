package nan.produced.prism.core.device.application.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.application.port.outbound.DeviceScreenshotRepository;
import nan.produced.prism.core.device.domain.screenshot.DeviceScreenshotEntity;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageUsageFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceScreenshotApplicationService {

    private final DeviceRepository deviceRepository;
    private final DeviceScreenshotRepository deviceScreenshotRepository;
    private final UserStorageUsageFacade userStorageUsageFacade;
    private final S3Client s3Client;

    @Value("${prism.media.s3.bucket}")
    private String s3Bucket;

    @Transactional
    public void recordScreenshotUploaded(
            Long deviceId,
            String s3Key,
            long sizeBytes,
            String contentType,
            Instant uploadedAt,
            String traceId) {

        if (deviceId == null || !StringUtils.hasText(s3Key)) {
            return;
        }

        UUID userId = deviceRepository.findUserIdByDeviceId(deviceId);
        if (userId == null) {
            log.warn("DeviceScreenshot - device not found in core, skip screenshot persist: deviceId={}, s3Key={}, traceId={}",
                    deviceId, s3Key, traceId);
            return;
        }

        Instant occurredAt = uploadedAt != null ? uploadedAt : Instant.now();
        long normalizedSize = Math.max(0L, sizeBytes);

        DeviceScreenshotEntity entity = DeviceScreenshotEntity.builder()
                .screenshotId(UUID.randomUUID())
                .deviceId(deviceId)
                .s3Key(s3Key)
                .sizeBytes(normalizedSize)
                .contentType(StringUtils.hasText(contentType) ? contentType.trim() : null)
                .uploadedAt(occurredAt)
                .build();

        deviceScreenshotRepository.save(entity);

        userStorageUsageFacade.incrementUsage(
                userId,
                StorageSourceType.SCREENSHOT,
                StorageFileType.IMAGE,
                1,
                normalizedSize);

        log.debug("DeviceScreenshot - recorded uploaded screenshot: deviceId={}, screenshotId={}, sizeBytes={}, s3Key={}, traceId={}",
                deviceId, entity.getScreenshotId(), normalizedSize, s3Key, traceId);
    }

    @Transactional(readOnly = true)
    public List<DeviceScreenshotEntity> listScreenshots(UUID userId, Long deviceId) {
        assertDeviceAccessible(userId, deviceId);
        return deviceScreenshotRepository.findByDeviceIdOrderByUploadedAtDesc(deviceId);
    }

    @Transactional
    public void deleteScreenshot(UUID userId, Long deviceId, UUID screenshotId) {
        assertDeviceAccessible(userId, deviceId);

        DeviceScreenshotEntity entity = deviceScreenshotRepository.findByScreenshotIdAndDeviceId(screenshotId, deviceId)
                .orElseThrow(() -> new BizException(ErrorCode.DEVICE_SCREENSHOT_NOT_FOUND));

        deleteObject(entity.getS3Key());
        deviceScreenshotRepository.delete(entity);

        userStorageUsageFacade.decrementUsage(
                userId,
                StorageSourceType.SCREENSHOT,
                StorageFileType.IMAGE,
                1,
                entity.getSizeBytes() != null ? entity.getSizeBytes() : 0L);
    }

    @Transactional
    public int clearScreenshots(UUID userId, Long deviceId) {
        assertDeviceAccessible(userId, deviceId);

        List<DeviceScreenshotEntity> screenshots = deviceScreenshotRepository.findByDeviceIdOrderByUploadedAtDesc(deviceId);
        if (screenshots == null || screenshots.isEmpty()) {
            return 0;
        }

        long totalBytes = 0L;
        for (DeviceScreenshotEntity screenshot : screenshots) {
            if (screenshot == null) {
                continue;
            }
            deleteObject(screenshot.getS3Key());
            if (screenshot.getSizeBytes() != null) {
                totalBytes += screenshot.getSizeBytes();
            }
        }

        deviceScreenshotRepository.deleteAll(screenshots);

        userStorageUsageFacade.decrementUsage(
                userId,
                StorageSourceType.SCREENSHOT,
                StorageFileType.IMAGE,
                screenshots.size(),
                totalBytes);

        return screenshots.size();
    }

    private void deleteObject(String s3Key) {
        if (!StringUtils.hasText(s3Key) || !StringUtils.hasText(s3Bucket)) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(s3Bucket)
                .key(s3Key)
                .build());
    }

    private void assertDeviceAccessible(UUID userId, Long deviceId) {
        if (userId == null || deviceId == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }
        if (deviceRepository.findByDeviceIdAndUserId(deviceId, userId) == null) {
            throw new BizException(ErrorCode.DEVICE_NOT_FOUND_IN_CORE);
        }
    }
}
