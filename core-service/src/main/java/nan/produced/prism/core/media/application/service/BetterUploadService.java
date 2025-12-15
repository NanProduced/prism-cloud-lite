package nan.produced.prism.core.media.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.dto.*;
import nan.produced.prism.core.media.application.exception.UploadValidationException;
import nan.produced.prism.core.media.application.port.outbound.ObjectStoragePort;
import nan.produced.prism.core.media.application.repository.MediaFolderRepository;
import nan.produced.prism.core.media.infrastructure.config.S3Properties;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties.RouteConfig;
import org.springframework.stereotype.Service;

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
            var fileInfo = buildSimpleUploadFileInfo(file, routeConfig, userId, metadataExtractor, expiration);
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
            String userId,
            UploadMetadataExtractor metadataExtractor,
            Duration expiration) {

        var key = generateObjectKey(routeConfig, userId, file, metadataExtractor);
        var metadata = metadataExtractor.buildObjectMetadata(file.getName());

        var signedUrl = objectStorage.generatePresignedPutUrl(key, file.getType(), metadata, expiration);

        return BetterUploadResponse.FileUploadInfo.builder()
                .signedUrl(signedUrl)
                .file(buildFileDetail(file, key, metadata))
                .headers(buildUploadHeaders(routeConfig))
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

        var key = generateObjectKey(routeConfig, userId, file, metadataExtractor);
        var metadata = metadataExtractor.buildObjectMetadata(file.getName());

        var uploadId = objectStorage.createMultipartUpload(key, file.getType(), metadata);
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
     * 格式: {prefix}/{userId}/{groupId}/{role}-{slug}.{ext}
     */
    private String generateObjectKey(
            RouteConfig routeConfig,
            String userId,
            BetterUploadRequest.FileInfo file,
            UploadMetadataExtractor metadataExtractor) {

        var fileName = file.getName();
        var prefix = routeConfig.getPathPrefix();
        var groupId = metadataExtractor.extractGroupId(fileName);
        var role = metadataExtractor.extractRole(fileName);
        var ext = extractFileExtension(fileName);
        var slug = generateSlug(fileName);

        return String.format(OBJECT_KEY_FORMAT, prefix, userId, groupId, role, slug, ext);
    }

    /**
     * 提取文件扩展名
     */
    private String extractFileExtension(String fileName) {
        var lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0) ? fileName.substring(lastDot + 1).toLowerCase() : "";
    }

    /**
     * 生成 URL 友好的 slug
     */
    private String generateSlug(String fileName) {
        var name = removeFileExtension(fileName);

        name = name.toLowerCase()
                .replaceAll(SLUG_ALLOWED_CHARS_PATTERN, "-")
                .replaceAll(SLUG_CONSECUTIVE_HYPHENS_PATTERN, "-")
                .replaceAll(SLUG_LEADING_TRAILING_HYPHENS_PATTERN, "");

        if (name.length() > SLUG_MAX_LENGTH) {
            name = name.substring(0, SLUG_MAX_LENGTH);
        }

        return name.isEmpty() ? String.valueOf(System.currentTimeMillis()) : name;
    }

    /**
     * 移除文件扩展名
     */
    private String removeFileExtension(String fileName) {
        var lastDot = fileName.lastIndexOf('.');
        return (lastDot > 0) ? fileName.substring(0, lastDot) : fileName;
    }

    /**
     * 构建上传请求头
     */
    private Map<String, String> buildUploadHeaders(RouteConfig routeConfig) {
        var headers = new HashMap<String, String>();
        headers.put(HEADER_STORAGE_CLASS, routeConfig.getStorageClass());
        headers.put(HEADER_ACL, routeConfig.isPublicAccess() ? ACL_PUBLIC_READ : ACL_PRIVATE);
        return headers;
    }
}
