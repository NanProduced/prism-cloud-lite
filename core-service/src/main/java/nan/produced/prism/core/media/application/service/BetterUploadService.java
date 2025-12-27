package nan.produced.prism.core.media.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.config.StoragePathProperties;
import nan.produced.prism.core.common.util.FileNameUtils;
import nan.produced.prism.core.common.util.ObjectKeyUtils;
import nan.produced.prism.core.media.application.dto.*;
import nan.produced.prism.core.media.application.exception.UploadValidationException;
import nan.produced.prism.core.media.application.port.outbound.ObjectStoragePort;
import nan.produced.prism.core.media.application.repository.MediaFolderRepository;
import nan.produced.prism.core.media.application.util.MediaLibraryObjectKeyUtils;
import nan.produced.prism.core.media.infrastructure.config.S3Properties;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties.RouteConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;

import static nan.produced.prism.core.media.application.service.BetterUploadConstant.*;

/**
 * 上传服务
 * <p>
 * 处理 Better Upload 协议请求，生成预签名 URL
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BetterUploadService {

    private final ObjectStoragePort objectStorage;
    private final S3Properties s3Properties;
    private final UploadRouteProperties uploadRouteProperties;
    private final StoragePathProperties storagePathProperties;
    private final MediaFolderRepository mediaFolderRepository;

    /**
     * 处理 Better Upload 请求
     *
     * @param request Better Upload 请求
     * @param userId  当前用户 ID
     * @return 上传响应（普通上传或 Multipart）
     */
    public Object processUploadRequest(BetterUploadRequest request, String userId) {
        var routeConfig = uploadRouteProperties.getRouteConfig(request.getRoute());
        var metadataExtractor = new UploadMetadataExtractor(request.getMetadata());

        validateRequest(request, routeConfig);
        validateFolder(metadataExtractor, userId);

        boolean needsMultipart = isMultipartRequired(request, routeConfig);

        if (needsMultipart) {
            return processMultipartUpload(request, routeConfig, userId, metadataExtractor);
        } else {
            return processSimpleUpload(request, routeConfig, userId, metadataExtractor);
        }
    }

    /**
     * 判断是否需要 Multipart 上传
     */
    private boolean isMultipartRequired(BetterUploadRequest request, RouteConfig routeConfig) {
        return routeConfig.isMultipartEnabled() &&
                request.getFiles().stream()
                        .anyMatch(f -> f.getSize() > routeConfig.getMultipartThreshold());
    }

    /**
     * 处理普通上传
     */
    private BetterUploadResponse processSimpleUpload(
            BetterUploadRequest request,
            RouteConfig routeConfig,
            String userId,
            UploadMetadataExtractor metadataExtractor) {

        var expiration = Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes());
        var fileInfos = new ArrayList<BetterUploadResponse.FileUploadInfo>();

        for (var file : request.getFiles()) {
            var fileInfo = buildSimpleUploadFileInfo(file, routeConfig, metadataExtractor, expiration);
            fileInfos.add(fileInfo);
        }

        log.info("Generated {} presigned URLs for user: {}", fileInfos.size(), userId);

        return BetterUploadResponse.builder()
                .files(fileInfos)
                .metadata(Collections.emptyMap())
                .build();
    }

    /**
     * 构建单个文件的普通上传信息
     */
    private BetterUploadResponse.FileUploadInfo buildSimpleUploadFileInfo(
            BetterUploadRequest.FileInfo file,
            RouteConfig routeConfig,
            UploadMetadataExtractor metadataExtractor,
            Duration expiration) {

        var key = generateObjectKey(routeConfig, file, metadataExtractor);
        var metadata = metadataExtractor.buildObjectMetadata(file.getName());

        var headers = buildUploadHeaders(routeConfig);
        // PutObject 预签名会将 content-type 作为 SignedHeaders 的一部分。
        // 客户端必须携带完全一致的 Content-Type，否则 S3 会返回 403（SignatureDoesNotMatch）。
        String contentType = null;
        if (file.getType() != null && !file.getType().isBlank()) {
            contentType = file.getType().trim();
            headers.put("content-type", contentType);
        }
        // PutObject 预签名会将 metadata 签入 SignedHeaders（x-amz-meta-*）。
        // 客户端必须在 PUT 请求中携带完全一致的 x-amz-meta-* 头，否则 S3 会返回 403（SignatureDoesNotMatch）。
        if (metadata != null && !metadata.isEmpty()) {
            metadata.forEach((k, v) -> {
                if (k != null && !k.isBlank() && v != null && !v.isBlank()) {
                    headers.put("x-amz-meta-" + k, v);
                }
            });
        }
        var signedUrl = objectStorage.generatePresignedPutUrl(
                key,
                contentType,
                metadata,
                expiration,
                headers.get(HEADER_STORAGE_CLASS),
                headers.get(HEADER_ACL)
        );

        return BetterUploadResponse.FileUploadInfo.builder()
                .signedUrl(signedUrl)
                .file(buildFileDetail(file, key, metadata))
                .headers(headers)
                .build();
    }

    /**
     * 处理 Multipart 上传
     */
    private BetterUploadMultipartResponse processMultipartUpload(
            BetterUploadRequest request,
            RouteConfig routeConfig,
            String userId,
            UploadMetadataExtractor metadataExtractor) {

        var expiration = Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes());
        var partSize = routeConfig.getMultipartPartSize();
        var fileInfos = new ArrayList<BetterUploadMultipartResponse.FileMultipartInfo>();

        for (var file : request.getFiles()) {
            var fileInfo = buildMultipartUploadFileInfo(file, routeConfig, userId, metadataExtractor, expiration, partSize);
            fileInfos.add(fileInfo);
        }

        log.info("Generated multipart upload for {} files, user: {}", fileInfos.size(), userId);

        return BetterUploadMultipartResponse.builder()
                .multipart(BetterUploadMultipartResponse.MultipartInfo.builder()
                        .files(fileInfos)
                        .partSize(partSize)
                        .build())
                .metadata(Collections.emptyMap())
                .build();
    }

    /**
     * 构建单个文件的 Multipart 上传信息
     */
    private BetterUploadMultipartResponse.FileMultipartInfo buildMultipartUploadFileInfo(
            BetterUploadRequest.FileInfo file,
            RouteConfig routeConfig,
            String userId,
            UploadMetadataExtractor metadataExtractor,
            Duration expiration,
            long partSize) {

        var key = generateObjectKey(routeConfig, file, metadataExtractor);
        var metadata = metadataExtractor.buildObjectMetadata(file.getName());

        var headers = buildUploadHeaders(routeConfig);
        var uploadId = objectStorage.createMultipartUpload(
                key,
                file.getType(),
                metadata,
                headers.get(HEADER_STORAGE_CLASS),
                headers.get(HEADER_ACL)
        );
        var parts = buildMultipartParts(key, uploadId, file.getSize(), partSize, expiration);

        var completeUrl = objectStorage.generatePresignedCompleteUrl(key, uploadId, expiration);
        var abortUrl = objectStorage.generatePresignedAbortUrl(key, uploadId, expiration);

        return BetterUploadMultipartResponse.FileMultipartInfo.builder()
                .file(buildFileDetail(file, key, metadata))
                .parts(parts)
                .uploadId(uploadId)
                .completeSignedUrl(completeUrl)
                .abortSignedUrl(abortUrl)
                .build();
    }

    /**
     * 构建 Multipart 分片信息列表
     */
    private List<BetterUploadMultipartResponse.PartInfo> buildMultipartParts(
            String key,
            String uploadId,
            long fileSize,
            long partSize,
            Duration expiration) {

        var totalParts = (int) Math.ceil((double) fileSize / partSize);
        var parts = new ArrayList<BetterUploadMultipartResponse.PartInfo>(totalParts);

        for (int partNumber = 1; partNumber <= totalParts; partNumber++) {
            var partUrl = objectStorage.generatePresignedPartUrl(key, uploadId, partNumber, expiration);
            var actualPartSize = calculatePartSize(fileSize, partSize, partNumber, totalParts);

            parts.add(BetterUploadMultipartResponse.PartInfo.builder()
                    .signedUrl(partUrl)
                    .partNumber(partNumber)
                    .size(actualPartSize)
                    .build());
        }

        return parts;
    }

    /**
     * 计算分片大小
     */
    private long calculatePartSize(long fileSize, long partSize, int partNumber, int totalParts) {
        return (partNumber == totalParts)
                ? fileSize - (partNumber - 1) * partSize
                : partSize;
    }

    /**
     * 构建文件详情
     */
    private BetterUploadResponse.FileDetail buildFileDetail(
            BetterUploadRequest.FileInfo file,
            String key,
            Map<String, String> metadata) {

        return BetterUploadResponse.FileDetail.builder()
                .name(file.getName())
                .size(file.getSize())
                .type(file.getType())
                .objectInfo(BetterUploadResponse.ObjectInfo.builder()
                        .key(key)
                        .metadata(metadata)
                        .cacheControl(CACHE_CONTROL_IMMUTABLE)
                        .build())
                .build();
    }

    /**
     * 校验文件夹正确性
     */
    private void validateFolder(UploadMetadataExtractor metadataExtractor, String userId) {
        var folderId = metadataExtractor.extractFolderId();
        if (!DEFAULT_FOLDER_ID.equals(folderId) &&
                !mediaFolderRepository.existsByIdAndUserId(folderId, UUID.fromString(userId))) {
            throw new IllegalArgumentException("Folder not found");
        }
    }

    /**
     * 验证请求
     */
    private void validateRequest(BetterUploadRequest request, RouteConfig routeConfig) {
        validateFileCount(request, routeConfig);
        request.getFiles().forEach(file -> validateFile(file, routeConfig));
    }

    /**
     * 验证文件数量
     */
    private void validateFileCount(BetterUploadRequest request, RouteConfig routeConfig) {
        if (request.getFiles().size() > routeConfig.getMaxFiles()) {
            throw new UploadValidationException(
                    BetterUploadErrorResponse.tooManyFiles(routeConfig.getMaxFiles())
            );
        }
    }

    /**
     * 验证单个文件
     */
    private void validateFile(BetterUploadRequest.FileInfo file, RouteConfig routeConfig) {
        if (file.getSize() > routeConfig.getMaxFileSize()) {
            throw new UploadValidationException(
                    BetterUploadErrorResponse.fileTooLarge(file.getName(), routeConfig.getMaxFileSize())
            );
        }

        if (!isTypeAllowed(file.getType(), routeConfig.getAllowedTypes())) {
            throw new UploadValidationException(
                    BetterUploadErrorResponse.invalidFileType(file.getName(), file.getType())
            );
        }
    }

    /**
     * 检查文件类型是否允许
     */
    private boolean isTypeAllowed(String type, List<String> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return true;
        }

        return allowedTypes.stream().anyMatch(allowed -> matchesMimeType(type, allowed));
    }

    /**
     * 匹配 MIME 类型
     */
    private boolean matchesMimeType(String type, String pattern) {
        if (pattern.endsWith(MIME_WILDCARD_SUFFIX)) {
            var prefix = pattern.substring(0, pattern.length() - 1);
            return type.startsWith(prefix);
        }
        return pattern.equals(type);
    }

    /**
     * 生成 S3 Object Key
     * <p>
     * 优先使用内容寻址（md5+size）生成稳定 key，便于全局去重与设备侧文件名校验：
     * <p>
     * {prefix}/files/F_{md5}_{size}.{ext}
     * <p>
     * 若 md5 缺失，则降级为临时上传路径：
     * <p>
     * {prefix}/uploads/{groupId}/{role}-{slug}.{ext}
     */
    private String generateObjectKey(
            RouteConfig routeConfig,
            BetterUploadRequest.FileInfo file,
            UploadMetadataExtractor metadataExtractor) {

        var fileName = file.getName();
        var prefix = routeConfig.getPathPrefix();

        String ext = FileNameUtils.resolveExtensionOrEmpty(fileName, file.getType());

        String md5 = normalizeMd5(metadataExtractor.extractMd5(fileName));
        long sizeBytes = file.getSize();
        if (StringUtils.hasText(md5) && sizeBytes > 0) {
            return MediaLibraryObjectKeyUtils.buildMediaLibraryFilesObjectKey(
                    routeConfig,
                    storagePathProperties.getMediaLibrary().getFilesDir(),
                    md5,
                    sizeBytes,
                    ext
            );
        }

        var groupId = metadataExtractor.extractGroupId(fileName);
        var role = metadataExtractor.extractRole(fileName);
        String slug = FileNameUtils.slugifyFileName(fileName, SLUG_MAX_LENGTH);
        String fallbackFilename = role + "-" + slug + (ext.isEmpty() ? "" : "." + ext);
        return ObjectKeyUtils.join(prefix, storagePathProperties.getMediaLibrary().getUploadsDir(), groupId, fallbackFilename);
    }

    private String normalizeMd5(String md5) {
        if (md5 == null) {
            return null;
        }
        String trimmed = md5.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // 设备侧文件名示例使用大写 MD5；这里统一用大写生成稳定 key 与文件名
        return trimmed.toUpperCase(Locale.ROOT);
    }

    /**
     * 构建上传请求头
     */
    private Map<String, String> buildUploadHeaders(RouteConfig routeConfig) {
        var headers = new HashMap<String, String>();
        headers.put(HEADER_STORAGE_CLASS, routeConfig.getStorageClass());

        // AWS S3 buckets commonly enable "Bucket owner enforced" (ACLs disabled).
        // In that case, including x-amz-acl will cause the presigned PUT to be rejected (403).
        if (s3Properties.isAclEnabled()) {
            headers.put(HEADER_ACL, routeConfig.isPublicAccess() ? ACL_PUBLIC_READ : ACL_PRIVATE);
        } else if (routeConfig.isPublicAccess()) {
            log.warn("BetterUpload - publicAccess=true but S3 ACL is disabled (prism.media.s3.aclEnabled=false); x-amz-acl will be omitted");
        }
        return headers;
    }
}
