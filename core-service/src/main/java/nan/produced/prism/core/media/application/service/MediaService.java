package nan.produced.prism.core.media.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.media.application.constant.MediaAssetSourceTypeConstant;
import nan.produced.prism.core.user.api.StorageFileTypeResolver;
import nan.produced.prism.core.media.application.domain.FileEntity;
import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import nan.produced.prism.core.media.application.dto.*;
import nan.produced.prism.core.media.application.repository.FileEntityRepository;
import nan.produced.prism.core.media.application.repository.MediaAssetRepository;
import nan.produced.prism.core.media.application.repository.MediaFolderRepository;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageUsageFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 媒体库服务
 * <p>
 * 处理秒传检查和批量落库等媒体库核心业务
 *
 * @author Nan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final FileEntityRepository fileEntityRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaFolderRepository mediaFolderRepository;
    private final UserStorageUsageFacade userStorageUsageFacade;

    /**
     * MD5 秒传检查
     * <p>
     * 批量检查文件是否已存在，支持秒传
     *
     * @param request 秒传检查请求
     * @return 检查结果
     */
    public DuplicateCheckResponse duplicateCheck(DuplicateCheckRequest request) {
        // 提取所有非空的 MD5 值
        var md5List = request.getFiles().stream()
                .map(DuplicateCheckRequest.FileCheckInfo::getMd5)
                .map(this::normalizeMd5)
                .filter(StringUtils::hasText)
                .toList();

        // 批量查询已存在的文件
        var existingFiles = fileEntityRepository.findByMd5In(md5List).stream()
                .collect(Collectors.toMap(FileEntity::getMd5, Function.identity()));

        // 构建检查结果
        var results = request.getFiles().stream()
                .map(file -> buildFileCheckResult(file, existingFiles))
                .toList();

        log.debug("DuplicateCheck completed: total={}, duplicates={}",
                results.size(),
                results.stream().filter(DuplicateCheckResponse.FileCheckResult::isDuplicate).count());

        return DuplicateCheckResponse.builder()
                .results(results)
                .build();
    }

    /**
     * 批量落库
     * <p>
     * 上传成功后落库，支持混合场景：部分秒传 + 部分新上传
     * 使用 groupId 作为幂等键
     *
     * @param request 落库请求
     * @param userId  用户ID
     * @return 落库结果
     */
    @Transactional
    public BatchFinalizeResponse batchFinalize(BatchFinalizeRequest request, UUID userId) {
        // 验证文件夹
        validateFolder(request.getFolderId(), userId);

        var assetResults = new ArrayList<BatchFinalizeResponse.AssetResult>();

        for (var item : request.getItems()) {
            var assetResult = processAssetItem(item, request.getFolderId(), userId);
            assetResults.add(assetResult);
        }

        log.debug("BatchFinalize completed: userId={}, assets={}, newCreated={}",
                userId, assetResults.size(),
                assetResults.stream().filter(r -> !r.isExisted()).count());

        return BatchFinalizeResponse.builder()
                .assets(assetResults)
                .build();
    }

    /**
     * 构建单个文件的检查结果
     */
    private DuplicateCheckResponse.FileCheckResult buildFileCheckResult(
            DuplicateCheckRequest.FileCheckInfo file,
            Map<String, FileEntity> existingFiles) {

        var builder = DuplicateCheckResponse.FileCheckResult.builder()
                .clientId(file.getClientId());

        String md5 = normalizeMd5(file.getMd5());
        if (StringUtils.hasText(md5)) {
            var existing = existingFiles.get(md5);
            if (existing != null) {
                return builder
                        .duplicate(true)
                        .fileEntityId(existing.getFileId())
                        .build();
            }
        }

        return builder
                .duplicate(false)
                .build();
    }

    /**
     * 验证文件夹存在性
     */
    private void validateFolder(String folderId, UUID userId) {
        if (!StringUtils.hasText(folderId)) {
            // null 或空字符串表示根目录，无需验证
            return;
        }

        if (!mediaFolderRepository.existsByIdAndUserId(folderId, userId)) {
            throw new BizException(ErrorCode.MEDIA_FOLDER_NOT_FOUND);
        }
    }

    /**
     * 处理单个素材条目
     */
    private BatchFinalizeResponse.AssetResult processAssetItem(
            BatchFinalizeRequest.AssetItem item,
            String folderId,
            UUID userId) {

        // 1. 幂等性检查：根据 groupId 查找已存在的素材
        var existingAsset = mediaAssetRepository.findByGroupId(item.getGroupId());
        if (existingAsset.isPresent()) {
            log.debug("Asset already exists, returning existing: groupId={}", item.getGroupId());
            return toAssetResult(existingAsset.get(), true);
        }

        // 2. 解析文件列表，找出 original 和 cover
        // 同时收集新上传文件的存储统计信息
        FileEntity originalFile = null;
        FileEntity coverFile = null;
        boolean hasInstantUpload = false;
        var storageIncrements = new EnumMap<StorageFileType, StorageIncrement>(StorageFileType.class);

        for (var fileItem : item.getFiles()) {
            var fileEntity = resolveFileEntity(fileItem);

            if (fileItem.isOriginal()) {
                originalFile = fileEntity;
            } else if (fileItem.isCover()) {
                coverFile = fileEntity;
            }

            if (fileItem.isInstantUpload()) {
                hasInstantUpload = true;
            } else {
                // 新上传的文件需要统计存储
                StorageFileType fileType;
                if (fileItem.isCover()) {
                    if (!isImageMimeType(fileItem.getType())) {
                        throw new BizException(ErrorCode.INVALID_REQUEST, "cover file must be image/*");
                    }
                    fileType = StorageFileType.COVER;
                } else {
                    fileType = StorageFileTypeResolver.fromMimeType(fileItem.getType());
                }
                storageIncrements.merge(fileType,
                        new StorageIncrement(1, fileItem.getSize()),
                        StorageIncrement::add);
            }
        }

        // 3. 验证必须有原始文件
        if (originalFile == null) {
            throw new BizException(ErrorCode.MEDIA_MISSING_ORIGINAL_FILE);
        }

        // 4. 确定来源类型：如果有任何秒传文件，标记为秒传来源
        int sourceType = hasInstantUpload ? MediaAssetSourceTypeConstant.INSTANT : MediaAssetSourceTypeConstant.UPLOAD;

        // 5. 创建素材实体
        var now = Instant.now();
        var asset = new MediaAssetEntity();
        asset.setId(UUID.randomUUID().toString());
        asset.setUserId(userId);
        asset.setGroupId(item.getGroupId());
        asset.setTitle(item.getTitle());
        asset.setFolderId(folderId);
        asset.setOriginalFile(originalFile);
        asset.setCoverFile(coverFile);
        asset.setSourceType(sourceType);
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);

        mediaAssetRepository.save(asset);

        // 6. 更新存储统计（只对新上传的文件）
        if (!storageIncrements.isEmpty()) {
            updateStorageUsage(userId, storageIncrements);
        }

        log.debug("Asset created: assetId={}, groupId={}, sourceType={}",
                asset.getId(), item.getGroupId(), sourceType);

        return toAssetResult(asset, false);
    }

    /**
     * 存储增量记录
     */
    private record StorageIncrement(int fileCount, long bytes) {
        StorageIncrement add(StorageIncrement other) {
            return new StorageIncrement(this.fileCount + other.fileCount, this.bytes + other.bytes);
        }
    }

    /**
     * 更新用户存储使用统计
     *
     * @param userId            用户ID
     * @param storageIncrements 按文件类型分组的存储增量
     */
    private void updateStorageUsage(UUID userId, Map<StorageFileType, StorageIncrement> storageIncrements) {
        for (var entry : storageIncrements.entrySet()) {
            var fileType = entry.getKey();
            var increment = entry.getValue();

            userStorageUsageFacade.incrementUsage(
                    userId,
                    StorageSourceType.MEDIA_LIBRARY,
                    fileType,
                    increment.fileCount(),
                    increment.bytes());
        }
    }

    /**
     * 解析文件实体
     * <p>
     * 根据请求中的信息解析或创建 FileEntity：
     * - 如果有 fileEntityId（秒传），增加引用计数并返回已有实体
     * - 如果有 s3Key（新上传），创建新的文件实体
     */
    private FileEntity resolveFileEntity(BatchFinalizeRequest.FileItem fileItem) {
        if (fileItem.isInstantUpload()) {
            // 秒传：查找已有文件并增加引用计数
            var fileEntity = fileEntityRepository.findById(fileItem.getFileEntityId())
                    .orElseThrow(() -> new BizException(ErrorCode.MEDIA_FILE_ENTITY_NOT_FOUND));

            fileEntityRepository.incrementRefCount(fileEntity.getFileId(), 1);
            log.debug("Instant upload: incremented refCount for fileId={}", fileEntity.getFileId());

            return fileEntity;
        }

        if (!StringUtils.hasText(fileItem.getS3Key())) {
            throw new BizException(ErrorCode.MEDIA_INVALID_FILE_REFERENCE);
        }

        // 新上传：创建 FileEntity
        var now = Instant.now();
        var fileEntity = new FileEntity();
        fileEntity.setFileId(UUID.randomUUID().toString());
        fileEntity.setMd5(normalizeMd5(fileItem.getMd5()));
        fileEntity.setS3Key(fileItem.getS3Key());
        fileEntity.setSize(fileItem.getSize());
        fileEntity.setMimeType(fileItem.getType());
        fileEntity.setRefCount(1);
        fileEntity.setWidth(fileItem.getWidth());
        fileEntity.setHeight(fileItem.getHeight());
        fileEntity.setDurationMs(fileItem.getDurationMs());
        fileEntity.setCreatedAt(now);

        fileEntityRepository.save(fileEntity);
        log.debug("New upload: created fileEntity fileId={}, s3Key={}", fileEntity.getFileId(), fileItem.getS3Key());

        return fileEntity;
    }

    private String normalizeMd5(String md5) {
        if (!StringUtils.hasText(md5)) {
            return null;
        }
        String trimmed = md5.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean isImageMimeType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return false;
        }
        return mimeType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    /**
     * 将素材实体转换为响应结果
     */
    private BatchFinalizeResponse.AssetResult toAssetResult(MediaAssetEntity asset, boolean existed) {
        return BatchFinalizeResponse.AssetResult.builder()
                .assetId(asset.getId())
                .groupId(asset.getGroupId())
                .title(asset.getTitle())
                .existed(existed)
                .sourceType(asset.getSourceType())
                .originalFile(toFileResult(asset.getOriginalFile()))
                .coverFile(asset.getCoverFile() != null ? toFileResult(asset.getCoverFile()) : null)
                .createdAt(asset.getCreatedAt())
                .build();
    }

    /**
     * 将文件实体转换为响应结果
     */
    private BatchFinalizeResponse.FileResult toFileResult(FileEntity file) {
        if (file == null) {
            return null;
        }
        return BatchFinalizeResponse.FileResult.builder()
                .fileId(file.getFileId())
                .mimeType(file.getMimeType())
                .size(file.getSize())
                .width(file.getWidth())
                .height(file.getHeight())
                .durationMs(file.getDurationMs())
                .build();
    }
}
