package nan.produced.prism.core.media.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.common.util.FileNameUtils;
import nan.produced.prism.core.user.api.StorageFileTypeResolver;
import nan.produced.prism.core.media.application.domain.MediaAssetEntity;
import nan.produced.prism.core.media.application.domain.MediaFolderEntity;
import nan.produced.prism.core.media.application.dto.MediaLibraryNodesResponse;
import nan.produced.prism.core.media.application.dto.MediaLibraryUsageResponse;
import nan.produced.prism.core.media.application.dto.MediaNodeDto;
import nan.produced.prism.core.media.application.dto.MoveNodesResponse;
import nan.produced.prism.core.media.application.port.outbound.MediaObjectUrlPort;
import nan.produced.prism.core.media.application.repository.FileEntityRepository;
import nan.produced.prism.core.media.application.repository.MediaAssetRepository;
import nan.produced.prism.core.media.application.repository.MediaFolderRepository;
import nan.produced.prism.core.system.api.SubscriptionQuotaFacade;
import nan.produced.prism.core.user.api.StorageFileType;
import nan.produced.prism.core.user.api.StorageSourceType;
import nan.produced.prism.core.user.api.UserStorageUsageFacade;
import nan.produced.prism.core.user.api.UserStorageUsageQueryFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaLibraryService {

    private static final int FOLDER_NAME_MAX_LENGTH = 64;

    private static final String NODE_TYPE_FOLDER = "folder";
    private static final String NODE_TYPE_ASSET = "asset";

    private final MediaFolderRepository mediaFolderRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final FileEntityRepository fileEntityRepository;
    private final UserStorageUsageFacade userStorageUsageFacade;
    private final UserStorageUsageQueryFacade userStorageUsageQueryFacade;
    private final SubscriptionQuotaFacade subscriptionQuotaFacade;
    private final MediaObjectUrlPort mediaObjectUrlPort;

    /**
     * 获取媒体库使用情况
     * @param userId 用户ID
     * @param tier 订阅等级
     * @return 媒体库使用情况
     */
    public MediaLibraryUsageResponse getUsage(UUID userId, String tier) {

        long quotaBytes = Optional.ofNullable(subscriptionQuotaFacade.getQuota(tier).storageLimitBytes()).orElse(0L);

        var usage = userStorageUsageQueryFacade.getUsage(userId, StorageSourceType.MEDIA_LIBRARY);
        long usedBytes = usage != null ? usage.totalBytes() : 0L;

        long folders = mediaFolderRepository.countByUserId(userId);

        var bytesByKind = defaultBytesByKind();
        var counts = defaultCounts(folders);

        if (usage != null && usage.items() != null) {
            for (var item : usage.items()) {
                if (item == null || item.fileType() == null) {
                    continue;
                }
                var kind = toAssetKind(item.fileType());
                bytesByKind.compute(kind, (k, v) -> (v == null ? 0L : v) + item.totalBytes());
                counts.compute(kind, (k, v) -> (v == null ? 0L : v) + item.fileCount());
            }
        }

        return MediaLibraryUsageResponse.builder()
                .quotaBytes(quotaBytes)
                .usedBytes(usedBytes)
                .bytesByKind(bytesByKind)
                .counts(counts)
                .build();
    }

    /**
     * 列出媒体库节点
     * @param userId 用户ID
     * @param parentId 可选；不传表示根目录
     * @param q 名称模糊匹配（前端当前在本地做，但后续可支持服务端）
     * @param sort 排序字段 updatedAt|name|size
     * @param limit 每页数量
     * @param cursor 游标
     * @return 节点列表
     */
    public MediaLibraryNodesResponse listNodes(
            UUID userId,
            String parentId,
            String q,
            String sort,
            Integer limit,
            String cursor) {

        if (userId == null) {
            return MediaLibraryNodesResponse.builder().items(Collections.emptyList()).nextCursor(null).build();
        }

        String normalizedParentId = normalizeFolderId(parentId);

        var folders = mediaFolderRepository.findByUserIdAndParentFolderId(userId, normalizedParentId);
        var assets = mediaAssetRepository.findWithFilesByUserIdAndFolderId(userId, normalizedParentId);

        var folderDtos = toFolderNodes(userId, folders);
        var assetDtos = assets.stream().map(this::toAssetNode).toList();

        var combined = new ArrayList<MediaNodeDto>(folderDtos.size() + assetDtos.size());
        combined.addAll(folderDtos);
        combined.addAll(assetDtos);

        var filtered = filterByQuery(combined, q);
        sortNodes(filtered, sort);

        var paged = applyCursorPagination(filtered, limit, cursor);
        return MediaLibraryNodesResponse.builder()
                .items(paged.items)
                .nextCursor(paged.nextCursor)
                .build();
    }

    public List<MediaNodeDto> listAllFolders(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }

        var folders = mediaFolderRepository.findAllByUserId(userId);
        if (folders.isEmpty()) {
            return Collections.emptyList();
        }

        var folderIdList = folders.stream().map(MediaFolderEntity::getFolderId).toList();
        var assetCounts = mediaAssetRepository.countAssetsByFolderIds(userId, folderIdList);

        var childFolderCounts = new HashMap<String, Long>();
        for (var folder : folders) {
            var parent = folder.getParentFolderId();
            if (StringUtils.hasText(parent)) {
                childFolderCounts.merge(parent, 1L, Long::sum);
            }
        }

        return folders.stream()
                .map(folder -> toFolderNode(folder,
                        Math.toIntExact(childFolderCounts.getOrDefault(folder.getFolderId(), 0L)
                                + assetCounts.getOrDefault(folder.getFolderId(), 0L))))
                .toList();
    }

    @Transactional
    public MediaNodeDto createFolder(UUID userId, String parentId, String name) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }

        var folderName = normalizeFolderName(name);
        var normalizedParentId = normalizeFolderId(parentId);

        String pathPrefix = "";
        if (StringUtils.hasText(normalizedParentId)) {
            var parent = mediaFolderRepository.findByIdAndUserId(normalizedParentId, userId)
                    .orElseThrow(() -> new BizException(ErrorCode.MEDIA_FOLDER_NOT_FOUND));
            pathPrefix = normalizePath(parent.getPath());
        }

        var now = Instant.now();
        var folder = new MediaFolderEntity();
        folder.setFolderId(UUID.randomUUID().toString());
        folder.setUserId(userId);
        folder.setName(folderName);
        folder.setParentFolderId(normalizedParentId);
        folder.setPath(buildPath(pathPrefix, folder.getFolderId()));
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);

        var saved = mediaFolderRepository.save(folder);
        return toFolderNode(saved, 0);
    }

    @Transactional
    public MediaNodeDto renameNode(UUID userId, String nodeId, String name) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (!StringUtils.hasText(nodeId)) {
            throw new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND);
        }

        var normalizedName = normalizeFolderName(name);

        var folder = mediaFolderRepository.findByIdAndUserId(nodeId, userId).orElse(null);
        if (folder != null) {
            folder.setName(normalizedName);
            folder.setUpdatedAt(Instant.now());
            var saved = mediaFolderRepository.save(folder);

            int childrenCount = getChildrenCount(userId, saved.getFolderId());
            return toFolderNode(saved, childrenCount);
        }

        var asset = mediaAssetRepository.findWithFilesByIdAndUserId(nodeId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND));

        asset.setTitle(normalizedName);
        asset.setUpdatedAt(Instant.now());
        var saved = mediaAssetRepository.save(asset);
        return toAssetNode(saved);
    }

    @Transactional
    public MoveNodesResponse moveNodes(UUID userId, List<String> nodeIds, String targetParentId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (nodeIds == null || nodeIds.isEmpty()) {
            return MoveNodesResponse.builder().moved(0).build();
        }

        String normalizedTargetParentId = normalizeFolderId(targetParentId);
        MediaFolderEntity targetParent = null;
        String targetPathPrefix = "";
        if (StringUtils.hasText(normalizedTargetParentId)) {
            targetParent = mediaFolderRepository.findByIdAndUserId(normalizedTargetParentId, userId)
                    .orElseThrow(() -> new BizException(ErrorCode.MEDIA_FOLDER_NOT_FOUND));
            targetPathPrefix = normalizePath(targetParent.getPath());
        }

        int moved = 0;
        for (var nodeId : nodeIds) {
            if (!StringUtils.hasText(nodeId)) {
                continue;
            }

            var folder = mediaFolderRepository.findByIdAndUserId(nodeId, userId).orElse(null);
            if (folder != null) {
                moveFolder(userId, folder, normalizedTargetParentId, targetPathPrefix, targetParent);
                moved++;
                continue;
            }

            var asset = mediaAssetRepository.findWithFilesByIdAndUserId(nodeId, userId)
                    .orElseThrow(() -> new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND));

            asset.setFolderId(normalizedTargetParentId);
            asset.setUpdatedAt(Instant.now());
            mediaAssetRepository.save(asset);
            moved++;
        }

        return MoveNodesResponse.builder().moved(moved).build();
    }

    @Transactional
    public void deleteNode(UUID userId, String nodeId) {
        if (userId == null) {
            throw new BizException(ErrorCode.NO_AUTHENTICATED_USER);
        }
        if (!StringUtils.hasText(nodeId)) {
            throw new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND);
        }

        var folder = mediaFolderRepository.findByIdAndUserId(nodeId, userId).orElse(null);
        if (folder != null) {
            deleteFolder(userId, folder);
            return;
        }

        var asset = mediaAssetRepository.findWithFilesByIdAndUserId(nodeId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.MEDIA_ASSET_NOT_FOUND));
        deleteAsset(userId, asset);
    }

    private void deleteFolder(UUID userId, MediaFolderEntity folder) {
        boolean hasChildFolder = mediaFolderRepository.existsByUserIdAndParentFolderId(userId, folder.getFolderId());
        boolean hasAssets = mediaAssetRepository.existsByUserIdAndFolderId(userId, folder.getFolderId());
        if (hasChildFolder || hasAssets) {
            throw new BizException(ErrorCode.MEDIA_FOLDER_NOT_EMPTY);
        }
        mediaFolderRepository.delete(folder);
    }

    private void deleteAsset(UUID userId, MediaAssetEntity asset) {
        var fileIds = new LinkedHashSet<String>();
        if (asset.getOriginalFile() != null) {
            fileIds.add(asset.getOriginalFile().getFileId());
        }
        if (asset.getCoverFile() != null) {
            fileIds.add(asset.getCoverFile().getFileId());
        }

        for (var fileId : fileIds) {
            var fileEntity = fileEntityRepository.findById(fileId).orElse(null);
            if (fileEntity == null) {
                continue;
            }

            int currentRef = fileEntity.getRefCount() != null ? fileEntity.getRefCount() : 0;
            int nextRef = Math.max(0, currentRef - 1);
            fileEntity.setRefCount(nextRef);
            fileEntityRepository.save(fileEntity);

            if (currentRef > 0 && nextRef == 0) {
                var fileType = StorageFileTypeResolver.fromMimeType(fileEntity.getMimeType());
                long bytes = fileEntity.getSize() != null ? fileEntity.getSize() : 0L;
                userStorageUsageFacade.incrementUsage(
                        userId,
                        StorageSourceType.MEDIA_LIBRARY,
                        fileType,
                        -1,
                        -bytes);
            }
        }

        mediaAssetRepository.delete(asset);
    }

    private void moveFolder(
            UUID userId,
            MediaFolderEntity folder,
            String targetParentId,
            String targetPathPrefix,
            MediaFolderEntity targetParent) {

        var folderId = folder.getFolderId();
        if (StringUtils.hasText(targetParentId) && folderId.equals(targetParentId)) {
            throw new BizException(ErrorCode.MEDIA_INVALID_MOVE_TARGET);
        }

        var oldPath = normalizePath(folder.getPath());

        if (targetParent != null) {
            var targetPath = normalizePath(targetParent.getPath());
            if (targetPath.startsWith(oldPath)) {
                throw new BizException(ErrorCode.MEDIA_INVALID_MOVE_TARGET);
            }
        }

        var newPath = buildPath(targetPathPrefix, folderId);

        var subtree = mediaFolderRepository.findByUserIdAndPathStartingWith(userId, oldPath);
        if (subtree.isEmpty()) {
            // fallback：至少更新自身
            folder.setParentFolderId(targetParentId);
            folder.setPath(newPath);
            folder.setUpdatedAt(Instant.now());
            mediaFolderRepository.save(folder);
            return;
        }

        var now = Instant.now();
        for (var item : subtree) {
            var itemPath = normalizePath(item.getPath());
            var suffix = itemPath.length() > oldPath.length()
                    ? itemPath.substring(oldPath.length())
                    : "";
            item.setPath(newPath + suffix);
            item.setUpdatedAt(now);
            if (folderId.equals(item.getFolderId())) {
                item.setParentFolderId(targetParentId);
            }
        }

        mediaFolderRepository.saveAll(subtree);
    }

    private List<MediaNodeDto> toFolderNodes(UUID userId, List<MediaFolderEntity> folders) {
        if (folders == null || folders.isEmpty()) {
            return Collections.emptyList();
        }
        var folderIds = folders.stream().map(MediaFolderEntity::getFolderId).toList();
        var childFolderCounts = mediaFolderRepository.countChildFoldersByParentIds(userId, folderIds);
        var assetCounts = mediaAssetRepository.countAssetsByFolderIds(userId, folderIds);

        var result = new ArrayList<MediaNodeDto>(folders.size());
        for (var folder : folders) {
            long childFolders = childFolderCounts.getOrDefault(folder.getFolderId(), 0L);
            long childAssets = assetCounts.getOrDefault(folder.getFolderId(), 0L);
            int childrenCount = Math.toIntExact(childFolders + childAssets);
            result.add(toFolderNode(folder, childrenCount));
        }
        return result;
    }

    private int getChildrenCount(UUID userId, String folderId) {
        var childFolderCounts = mediaFolderRepository.countChildFoldersByParentIds(userId, List.of(folderId));
        var assetCounts = mediaAssetRepository.countAssetsByFolderIds(userId, List.of(folderId));
        return Math.toIntExact(childFolderCounts.getOrDefault(folderId, 0L) + assetCounts.getOrDefault(folderId, 0L));
    }

    private MediaNodeDto toFolderNode(MediaFolderEntity folder, int childrenCount) {
        return MediaNodeDto.builder()
                .id(folder.getFolderId())
                .name(folder.getName())
                .parentId(folder.getParentFolderId())
                .createdAt(toIso(folder.getCreatedAt()))
                .updatedAt(toIso(folder.getUpdatedAt()))
                .type(NODE_TYPE_FOLDER)
                .childrenCount(childrenCount)
                .build();
    }

    private MediaNodeDto toAssetNode(MediaAssetEntity asset) {
        var original = asset.getOriginalFile();
        var cover = asset.getCoverFile();

        String mimeType = original != null ? original.getMimeType() : null;
        long sizeBytes = original != null && original.getSize() != null ? original.getSize() : 0L;

        String originalKey = original != null ? original.getS3Key() : null;
        String coverKey = cover != null ? cover.getS3Key() : null;

        return MediaNodeDto.builder()
                .id(asset.getId())
                .name(asset.getTitle())
                .parentId(asset.getFolderId())
                .createdAt(toIso(asset.getCreatedAt()))
                .updatedAt(toIso(asset.getUpdatedAt()))
                .type(NODE_TYPE_ASSET)
                .assetKind(determineAssetKind(mimeType))
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .extension(FileNameUtils.tryGetExtension(originalKey))
                .coverUrl(mediaObjectUrlPort.toPublicUrl(coverKey))
                .assetUrl(mediaObjectUrlPort.toPublicUrl(originalKey))
                .width(original != null ? original.getWidth() : null)
                .height(original != null ? original.getHeight() : null)
                .durationMs(original != null ? original.getDurationMs() : null)
                .build();
    }

    private String determineAssetKind(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return "other";
        }
        var lower = mimeType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("image/")) {
            return "image";
        }
        if (lower.startsWith("video/")) {
            return "video";
        }
        if (lower.startsWith("application/pdf")
                || lower.startsWith("application/msword")
                || lower.startsWith("application/vnd.")) {
            return "document";
        }
        return "other";
    }

    private String toAssetKind(StorageFileType fileType) {
        if (fileType == null) {
            return "other";
        }
        return switch (fileType) {
            case IMAGE -> "image";
            case VIDEO -> "video";
            case DOCUMENT -> "document";
            case AUDIO, VSN, OTHER -> "other";
        };
    }

    private List<MediaNodeDto> filterByQuery(List<MediaNodeDto> nodes, String q) {
        if (nodes == null || nodes.isEmpty() || !StringUtils.hasText(q)) {
            return nodes == null ? Collections.emptyList() : nodes;
        }
        var keyword = q.trim().toLowerCase(Locale.ROOT);
        if (keyword.isEmpty()) {
            return nodes;
        }
        return nodes.stream()
                .filter(n -> n.getName() != null && n.getName().toLowerCase(Locale.ROOT).contains(keyword))
                .toList();
    }

    private void sortNodes(List<MediaNodeDto> nodes, String sort) {
        if (nodes == null || nodes.size() <= 1) {
            return;
        }

        var normalized = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);

        Comparator<MediaNodeDto> folderFirst = Comparator
                .comparing((MediaNodeDto n) -> NODE_TYPE_FOLDER.equals(n.getType()) ? 0 : 1);

        Comparator<MediaNodeDto> folderComparator;
        Comparator<MediaNodeDto> assetComparator;

        switch (normalized) {
            case "name" -> {
                folderComparator = Comparator.comparing(MediaNodeDto::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                assetComparator = folderComparator;
            }
            case "size" -> {
                folderComparator = Comparator.comparing(MediaNodeDto::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
                assetComparator = Comparator.comparing(MediaNodeDto::getSizeBytes, Comparator.nullsLast(Comparator.reverseOrder()));
            }
            case "updatedat" -> {
                folderComparator = Comparator.comparing(MediaNodeDto::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
                assetComparator = folderComparator;
            }
            default -> {
                folderComparator = Comparator.comparing(MediaNodeDto::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
                assetComparator = folderComparator;
            }
        }

        nodes.sort((a, b) -> {
            int typeCmp = folderFirst.compare(a, b);
            if (typeCmp != 0) {
                return typeCmp;
            }

            if (NODE_TYPE_FOLDER.equals(a.getType())) {
                int cmp = folderComparator.compare(a, b);
                if (cmp != 0) {
                    return cmp;
                }
                return Comparator.comparing(MediaNodeDto::getId, Comparator.nullsLast(String::compareTo)).compare(a, b);
            }

            int cmp = assetComparator.compare(a, b);
            if (cmp != 0) {
                return cmp;
            }
            return Comparator.comparing(MediaNodeDto::getId, Comparator.nullsLast(String::compareTo)).compare(a, b);
        });
    }

    private record PagedResult(List<MediaNodeDto> items, String nextCursor) {
    }

    private PagedResult applyCursorPagination(List<MediaNodeDto> items, Integer limit, String cursor) {
        if (items == null || items.isEmpty()) {
            return new PagedResult(Collections.emptyList(), null);
        }

        int safeLimit = (limit == null || limit <= 0) ? items.size() : Math.min(limit, 500);
        int offset = parseCursor(cursor);
        if (offset < 0) {
            offset = 0;
        }
        if (offset >= items.size()) {
            return new PagedResult(Collections.emptyList(), null);
        }

        int end = Math.min(items.size(), offset + safeLimit);
        var page = items.subList(offset, end);
        String nextCursor = end < items.size() ? String.valueOf(end) : null;
        return new PagedResult(page, nextCursor);
    }

    private int parseCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        try {
            return Integer.parseInt(cursor.trim());
        } catch (Exception ignore) {
            return 0;
        }
    }

    private Map<String, Long> defaultBytesByKind() {
        var map = new LinkedHashMap<String, Long>();
        map.put("image", 0L);
        map.put("video", 0L);
        map.put("document", 0L);
        map.put("other", 0L);
        return map;
    }

    private Map<String, Long> defaultCounts(long folders) {
        var map = new LinkedHashMap<String, Long>();
        map.put("image", 0L);
        map.put("video", 0L);
        map.put("document", 0L);
        map.put("other", 0L);
        map.put("folders", folders);
        return map;
    }

    private String toIso(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private String normalizeFolderId(String folderId) {
        if (!StringUtils.hasText(folderId)) {
            return null;
        }
        return folderId.trim();
    }

    private String normalizeFolderName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.MEDIA_INVALID_FOLDER_NAME);
        }
        var trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(ErrorCode.MEDIA_INVALID_FOLDER_NAME);
        }
        if (trimmed.length() > FOLDER_NAME_MAX_LENGTH) {
            throw new BizException(ErrorCode.MEDIA_INVALID_FOLDER_NAME);
        }
        return trimmed;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        var normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }

    private String buildPath(String prefix, String folderId) {
        var normalizedPrefix = normalizePath(prefix);
        if (!StringUtils.hasText(normalizedPrefix)) {
            return "/" + folderId;
        }
        return normalizedPrefix + "/" + folderId;
    }

}
