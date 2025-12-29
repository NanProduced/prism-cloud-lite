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
import org.springframework.util.StringUtils;

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
    public String generatePresignedPutUrl(String key,
                                          String contentType,
                                          Map<String, String> metadata,
                                          Duration expiration,
                                          String storageClass,
                                          String acl) {

        StorageClass parsedStorageClass = parseStorageClass(storageClass);
        ObjectCannedACL parsedAcl = parseAcl(acl);

        var putObjectRequestBuilder = PutObjectRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .metadata(metadata);
        if (parsedStorageClass != null) {
            putObjectRequestBuilder.storageClass(parsedStorageClass);
        }
        if (parsedAcl != null) {
            putObjectRequestBuilder.acl(parsedAcl);
        }

        var presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .putObjectRequest(putObjectRequestBuilder.build())
                .build();

        var presignedRequest = s3Presigner.presignPutObject(presignRequest);
        log.debug("Generated presigned PUT URL for key: {}", key);
        return presignedRequest.url().toString();
    }

    @Override
    public String createMultipartUpload(String key,
                                        String contentType,
                                        Map<String, String> metadata,
                                        String storageClass,
                                        String acl) {
        StorageClass parsedStorageClass = parseStorageClass(storageClass);
        ObjectCannedACL parsedAcl = parseAcl(acl);
        var createRequestBuilder = CreateMultipartUploadRequest.builder()
                .bucket(s3Properties.getBucket())
                .key(key)
                .contentType(contentType)
                .metadata(metadata);
        if (parsedStorageClass != null) {
            createRequestBuilder.storageClass(parsedStorageClass);
        }
        if (parsedAcl != null) {
            createRequestBuilder.acl(parsedAcl);
        }

        var createRequest = createRequestBuilder.build();

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
        } catch (Exception ex) {
            log.warn("S3 headObject failed, treat as non-exist: key={}", key, ex);
            return false;
        }
    }

    @Override
    public void deleteObject(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(key)
                    .build());
        } catch (Exception ex) {
            log.warn("S3 deleteObject failed (ignored): key={}", key, ex);
        }
    }

    private StorageClass parseStorageClass(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return StorageClass.fromValue(value.trim());
        } catch (Exception ex) {
            log.warn("Unknown S3 storageClass '{}', fallback to default", value);
            return null;
        }
    }

    private ObjectCannedACL parseAcl(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ObjectCannedACL.fromValue(value.trim());
        } catch (Exception ex) {
            log.warn("Unknown S3 ACL '{}', fallback to default", value);
            return null;
        }
    }
}
