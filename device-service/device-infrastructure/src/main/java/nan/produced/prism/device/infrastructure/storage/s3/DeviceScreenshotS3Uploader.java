package nan.produced.prism.device.infrastructure.storage.s3;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import nan.produced.prism.device.common.utils.ContentTypeUtils;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
public class DeviceScreenshotS3Uploader {

    private static final String ROOT_PREFIX = "screenshot";

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public UploadResult upload(Long deviceId, byte[] bytes, String contentType) {
        if (deviceId == null || bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("deviceId/bytes is required");
        }

        String normalizedContentType = ContentTypeUtils.normalize(contentType, "image/jpeg");

        String deviceFolderKey = folderKey(deviceId);
        ensureFolder(deviceFolderKey);

        String objectKey = buildObjectKey(deviceId, normalizedContentType);

        var request = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(objectKey)
                .contentType(normalizedContentType)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(bytes));

        return new UploadResult(objectKey, bytes.length, normalizedContentType, Instant.now());
    }

    private void ensureFolder(String folderKey) {
        var request = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(folderKey)
                .contentType("application/x-directory")
                .build();

        s3Client.putObject(request, RequestBody.empty());
    }

    private String folderKey(Long deviceId) {
        return ROOT_PREFIX + "/" + deviceId + "/";
    }

    private String buildObjectKey(Long deviceId, String contentType) {
        String ext = ContentTypeUtils.guessExtensionOrDefault(contentType, "bin");
        String filename = Instant.now().toEpochMilli() + "-" + UUID.randomUUID() + "." + ext;
        return ROOT_PREFIX + "/" + deviceId + "/" + filename;
    }

    /**
     * 上传结果
     * @param s3Key s3 key
     * @param sizeBytes 文件大小
     * @param contentType 文件类型
     * @param uploadedAt 上传时间
     */
    public record UploadResult(String s3Key, long sizeBytes, String contentType, Instant uploadedAt) {
    }
}
