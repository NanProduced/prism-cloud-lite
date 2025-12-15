package nan.produced.prism.core.media.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.media.application.dto.*;
import nan.produced.prism.core.media.application.port.outbound.ObjectStoragePort;
import nan.produced.prism.core.media.infrastructure.config.S3Properties;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties;
import nan.produced.prism.core.media.infrastructure.config.UploadRouteProperties.RouteConfig;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 上传服务
 *
 * 处理 Better Upload 协议请求，生成预签名 URL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private final ObjectStoragePort objectStorage;
    private final S3Properties s3Properties;
    private final UploadRouteProperties uploadRouteProperties;

    /**
     * 处理 Better Upload 请求
     *
     * @param request Better Upload 请求
     * @param userId  当前用户 ID
     * @return 上传响应（普通上传或 Multipart）
     */
    public Object processUploadRequest(BetterUploadRequest request, String userId) {
        var routeConfig = uploadRouteProperties.getRouteConfig(request.getRoute());

        // 验证请求
        validateRequest(request, routeConfig);

        // 判断是否需要 Multipart 上传
        boolean needsMultipart = routeConfig.isMultipartEnabled() &&
                request.getFiles().stream()
                        .anyMatch(f -> f.getSize() > routeConfig.getMultipartThreshold());

        if (needsMultipart) {
            return processMultipartUpload(request, routeConfig, userId);
        } else {
            return processSimpleUpload(request, routeConfig, userId);
        }
    }

    /**
     * 处理普通上传
     */
    private BetterUploadResponse processSimpleUpload(
            BetterUploadRequest request,
            RouteConfig routeConfig,
            String userId) {

        var expiration = Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes());
        var fileInfos = new ArrayList<BetterUploadResponse.FileUploadInfo>();

        for (var file : request.getFiles()) {
            var key = generateObjectKey(routeConfig, userId, file, request.getMetadata());
            var metadata = buildObjectMetadata(file, request.getMetadata());

            var signedUrl = objectStorage.generatePresignedPutUrl(
                    key,
                    file.getType(),
                    metadata,
                    expiration
            );

            var fileInfo = BetterUploadResponse.FileUploadInfo.builder()
                    .signedUrl(signedUrl)
                    .file(BetterUploadResponse.FileDetail.builder()
                            .name(file.getName())
                            .size(file.getSize())
                            .type(file.getType())
                            .objectInfo(BetterUploadResponse.ObjectInfo.builder()
                                    .key(key)
                                    .metadata(metadata)
                                    .cacheControl("public, max-age=31536000")
                                    .build())
                            .build())
                    .headers(buildUploadHeaders(routeConfig))
                    .build();

            fileInfos.add(fileInfo);
        }

        log.info("Generated {} presigned URLs for user: {}", fileInfos.size(), userId);

        return BetterUploadResponse.builder()
                .files(fileInfos)
                .metadata(Collections.emptyMap())
                .build();
    }

    /**
     * 处理 Multipart 上传
     */
    private BetterUploadMultipartResponse processMultipartUpload(
            BetterUploadRequest request,
            RouteConfig routeConfig,
            String userId) {

        var expiration = Duration.ofMinutes(s3Properties.getPresignedUrlExpirationMinutes());
        var partSize = routeConfig.getMultipartPartSize();
        var fileInfos = new ArrayList<BetterUploadMultipartResponse.FileMultipartInfo>();

        for (var file : request.getFiles()) {
            var key = generateObjectKey(routeConfig, userId, file, request.getMetadata());
            var metadata = buildObjectMetadata(file, request.getMetadata());

            // 创建 Multipart 上传
            var uploadId = objectStorage.createMultipartUpload(key, file.getType(), metadata);

            // 计算分片数量
            var totalParts = (int) Math.ceil((double) file.getSize() / partSize);
            var parts = new ArrayList<BetterUploadMultipartResponse.PartInfo>();

            for (int i = 1; i <= totalParts; i++) {
                var partUrl = objectStorage.generatePresignedPartUrl(key, uploadId, i, expiration);
                var partSizeActual = (i == totalParts)
                        ? file.getSize() - (long) (i - 1) * partSize
                        : partSize;

                parts.add(BetterUploadMultipartResponse.PartInfo.builder()
                        .signedUrl(partUrl)
                        .partNumber(i)
                        .size(partSizeActual)
                        .build());
            }

            // 生成完成和取消 URL
            var completeUrl = objectStorage.generatePresignedCompleteUrl(key, uploadId, expiration);
            var abortUrl = objectStorage.generatePresignedAbortUrl(key, uploadId, expiration);

            var fileInfo = BetterUploadMultipartResponse.FileMultipartInfo.builder()
                    .file(BetterUploadResponse.FileDetail.builder()
                            .name(file.getName())
                            .size(file.getSize())
                            .type(file.getType())
                            .objectInfo(BetterUploadResponse.ObjectInfo.builder()
                                    .key(key)
                                    .metadata(metadata)
                                    .cacheControl("public, max-age=31536000")
                                    .build())
                            .build())
                    .parts(parts)
                    .uploadId(uploadId)
                    .completeSignedUrl(completeUrl)
                    .abortSignedUrl(abortUrl)
                    .build();

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
     * 验证请求
     */
    private void validateRequest(BetterUploadRequest request, RouteConfig routeConfig) {
        // 验证文件数量
        if (request.getFiles().size() > routeConfig.getMaxFiles()) {
            throw new UploadValidationException(
                    BetterUploadErrorResponse.tooManyFiles(routeConfig.getMaxFiles())
            );
        }

        // 验证每个文件
        for (var file : request.getFiles()) {
            // 验证文件大小
            if (file.getSize() > routeConfig.getMaxFileSize()) {
                throw new UploadValidationException(
                        BetterUploadErrorResponse.fileTooLarge(file.getName(), routeConfig.getMaxFileSize())
                );
            }

            // 验证文件类型
            if (!isTypeAllowed(file.getType(), routeConfig.getAllowedTypes())) {
                throw new UploadValidationException(
                        BetterUploadErrorResponse.invalidFileType(file.getName(), file.getType())
                );
            }
        }
    }

    /**
     * 检查文件类型是否允许
     */
    private boolean isTypeAllowed(String type, List<String> allowedTypes) {
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            return true;
        }

        for (var allowed : allowedTypes) {
            if (allowed.endsWith("/*")) {
                // 通配符匹配，如 "video/*"
                var prefix = allowed.substring(0, allowed.length() - 1);
                if (type.startsWith(prefix)) {
                    return true;
                }
            } else if (allowed.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成 S3 Object Key
     *
     * 格式: {prefix}/{userId}/{folderId}/{groupId}/{role}-{slug}.{ext}
     */
    private String generateObjectKey(
            RouteConfig routeConfig,
            String userId,
            BetterUploadRequest.FileInfo file,
            Map<String, Object> metadata) {

        var prefix = routeConfig.getPathPrefix();
        var folderId = extractString(metadata, "folderId", "default");
        var groupId = extractGroupId(file.getName(), metadata);
        var role = extractRole(file.getName(), metadata);

        // 从文件名提取扩展名
        var fileName = file.getName();
        var ext = "";
        var lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            ext = fileName.substring(lastDot + 1).toLowerCase();
        }

        // 生成 slug（简化文件名）
        var slug = generateSlug(fileName);

        return String.format("%s/%s/%s/%s/%s-%s.%s",
                prefix, userId, folderId, groupId, role, slug, ext);
    }

    /**
     * 从 metadata 中提取 groupId
     */
    @SuppressWarnings("unchecked")
    private String extractGroupId(String fileName, Map<String, Object> metadata) {
        if (metadata == null) {
            return UUID.randomUUID().toString();
        }

        var items = metadata.get("items");
        if (items instanceof List<?> list) {
            for (var item : list) {
                if (item instanceof Map<?, ?> map) {
                    var name = (String) map.get("name");
                    if (fileName.equals(name)) {
                        var groupId = (String) map.get("groupId");
                        if (StringUtils.hasText(groupId)) {
                            return groupId;
                        }
                    }
                }
            }
        }

        return UUID.randomUUID().toString();
    }

    /**
     * 从 metadata 中提取 role
     */
    @SuppressWarnings("unchecked")
    private String extractRole(String fileName, Map<String, Object> metadata) {
        if (metadata == null) {
            return "original";
        }

        var items = metadata.get("items");
        if (items instanceof List<?> list) {
            for (var item : list) {
                if (item instanceof Map<?, ?> map) {
                    var name = (String) map.get("name");
                    if (fileName.equals(name)) {
                        var role = (String) map.get("role");
                        if (StringUtils.hasText(role)) {
                            return role;
                        }
                    }
                }
            }
        }

        return "original";
    }

    private String extractString(Map<String, Object> metadata, String key, String defaultValue) {
        if (metadata == null) {
            return defaultValue;
        }
        var value = metadata.get(key);
        if (value instanceof String str && StringUtils.hasText(str)) {
            return str;
        }
        return defaultValue;
    }

    /**
     * 生成 URL 友好的 slug
     */
    private String generateSlug(String fileName) {
        // 移除扩展名
        var name = fileName;
        var lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            name = name.substring(0, lastDot);
        }

        // 转换为小写，替换非字母数字为连字符
        name = name.toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        // 限制长度
        if (name.length() > 50) {
            name = name.substring(0, 50);
        }

        // 如果为空，使用时间戳
        if (name.isEmpty()) {
            name = String.valueOf(System.currentTimeMillis());
        }

        return name;
    }

    /**
     * 构建对象元数据
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> buildObjectMetadata(
            BetterUploadRequest.FileInfo file,
            Map<String, Object> requestMetadata) {

        var metadata = new HashMap<String, String>();
        metadata.put("original-name", file.getName());

        if (requestMetadata != null) {
            var items = requestMetadata.get("items");
            if (items instanceof List<?> list) {
                for (var item : list) {
                    if (item instanceof Map<?, ?> map) {
                        var name = (String) map.get("name");
                        if (file.getName().equals(name)) {
                            var groupId = (String) map.get("groupId");
                            var role = (String) map.get("role");
                            var md5 = (String) map.get("md5");

                            if (StringUtils.hasText(groupId)) {
                                metadata.put("asset-group-id", groupId);
                            }
                            if (StringUtils.hasText(role)) {
                                metadata.put("role", role);
                            }
                            if (StringUtils.hasText(md5)) {
                                metadata.put("content-md5", md5);
                            }
                            break;
                        }
                    }
                }
            }
        }

        return metadata;
    }

    /**
     * 构建上传请求头
     */
    private Map<String, String> buildUploadHeaders(RouteConfig routeConfig) {
        var headers = new HashMap<String, String>();
        headers.put("x-amz-storage-class", routeConfig.getStorageClass());

        if (routeConfig.isPublicAccess()) {
            headers.put("x-amz-acl", "public-read");
        } else {
            headers.put("x-amz-acl", "private");
        }

        return headers;
    }

    /**
     * 上传验证异常
     */
    public static class UploadValidationException extends RuntimeException {
        private final BetterUploadErrorResponse errorResponse;

        public UploadValidationException(BetterUploadErrorResponse errorResponse) {
            super(errorResponse.getError().getMessage());
            this.errorResponse = errorResponse;
        }

        public BetterUploadErrorResponse getErrorResponse() {
            return errorResponse;
        }
    }
}
