package nan.produced.prism.core.media.infrastructure.adapter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.port.outbound.ObjectStoragePort;
import nan.produced.prism.core.media.infrastructure.config.S3Properties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.*;

import java.time.Duration;
import java.util.Map;

/**
 * S3 对象存储适配器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class S3ObjectStorageAdapter implements ObjectStoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public String generatePresignedPutUrl(String key, String contentType, Map<String, String> metadata, Duration expiration) {

        var presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(r -> r
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .metadata(metadata)
                        .build())
                .build();

        var presignedRequest = s3Presigner.presignPutObject(presignRequest);
        log.debug("Generated presigned PUT URL for key: {}", key);
        return presignedRequest.url().toString();
    }

    @Override
    public String createMultipartUpload(String key, String contentType, Map<String, String> metadata) {
        var createRequest = CreateMultipartUploadRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .metadata(metadata)
                .build();

        var response = s3Client.createMultipartUpload(createRequest);
        log.debug("Created multipart upload for key: {}, uploadId: {}", key, response.uploadId());
        return response.uploadId();
    }

    @Override
    public String generatePresignedPartUrl(String key, String uploadId, int partNumber, Duration expiration) {

        var presignRequest = UploadPartPresignRequest.builder()
                .signatureDuration(expiration)
                .uploadPartRequest(r -> r
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build())
                .build();

        var presignedRequest = s3Presigner.presignUploadPart(presignRequest);
        log.debug("Generated presigned part URL for key: {}, part: {}", key, partNumber);
        return presignedRequest.url().toString();
    }

    @Override
    public String generatePresignedCompleteUrl(String key, String uploadId, Duration expiration) {

        var presignRequest = CompleteMultipartUploadPresignRequest.builder()
                .signatureDuration(expiration)
                .completeMultipartUploadRequest(r -> r
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .uploadId(uploadId)
                        .build())
                .build();

        var presignedRequest = s3Presigner.presignCompleteMultipartUpload(presignRequest);
        log.debug("Generated presigned complete URL for key: {}", key);
        return presignedRequest.url().toString();
    }

    @Override
    public String generatePresignedAbortUrl(String key, String uploadId, Duration expiration) {
        var presignRequest = AbortMultipartUploadPresignRequest.builder()
                .signatureDuration(expiration)
                .abortMultipartUploadRequest(r -> r
                        .bucket(s3Properties.getBucket())
                        .key(key)
                        .uploadId(uploadId))
                .build();

        var presignedRequest = s3Presigner.presignAbortMultipartUpload(presignRequest);
        log.debug("Generated presigned abort URL for key: {}", key);
        return presignedRequest.url().toString();
    }

    @Override
    public boolean objectExists(String key) {
        try {
            var headRequest = HeadObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build();
            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
