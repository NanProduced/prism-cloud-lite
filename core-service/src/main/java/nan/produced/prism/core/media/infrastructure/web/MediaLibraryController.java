package nan.produced.prism.core.media.infrastructure.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.media.application.dto.*;
import nan.produced.prism.core.media.application.service.MediaLibraryService;
import nan.produced.prism.core.security.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Media Library（素材库）接口
 *
 * 用于前端 /dashboard/media 页面：目录浏览、文件夹管理、移动/删除、配额统计等。
 */
@Slf4j
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/media-library")
public class MediaLibraryController {

    private final MediaLibraryService mediaLibraryService;

    /**
     * 获取配额与使用统计
     */
    @GetMapping("/usage")
    public ResponseEntity<BffResponse<MediaLibraryUsageResponse>> getUsage() {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        var response = mediaLibraryService.getUsage(userId, user.tier());
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 列出目录节点（文件夹优先）
     */
    @GetMapping("/nodes")
    public ResponseEntity<BffResponse<MediaLibraryNodesResponse>> listNodes(
            @RequestParam(value = "parentId", required = false) String parentId,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "cursor", required = false) String cursor) {

        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        var response = mediaLibraryService.listNodes(userId, parentId, q, sort, limit, cursor);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 获取全部文件夹（用于上传目的地 Tree）
     */
    @GetMapping("/folders")
    public ResponseEntity<BffResponse<List<MediaNodeDto>>> listFolders() {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        var response = mediaLibraryService.listAllFolders(userId);
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 创建文件夹
     */
    @PostMapping("/folders")
    public ResponseEntity<BffResponse<MediaNodeDto>> createFolder(@RequestBody @Validated CreateFolderRequest request) {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        var folder = mediaLibraryService.createFolder(userId, request.getParentId(), request.getName());
        return ResponseEntity.ok(BffResponse.success(folder).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 重命名（文件夹/素材）
     */
    @PatchMapping("/nodes/{id}")
    public ResponseEntity<BffResponse<MediaNodeDto>> renameNode(
            @PathVariable("id") String id,
            @RequestBody @Validated RenameNodeRequest request) {

        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        var updated = mediaLibraryService.renameNode(userId, id, request.getName());
        return ResponseEntity.ok(BffResponse.success(updated).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 移动（目录结构变更，不重新上传）
     */
    @PostMapping("/nodes/move")
    public ResponseEntity<BffResponse<MoveNodesResponse>> moveNodes(@RequestBody @Validated MoveNodesRequest request) {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        var response = mediaLibraryService.moveNodes(userId, request.getNodeIds(), request.getTargetParentId());
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 删除（文件夹/素材）
     */
    @DeleteMapping("/nodes/{id}")
    public ResponseEntity<BffResponse<Void>> deleteNode(@PathVariable("id") String id) {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        mediaLibraryService.deleteNode(userId, id);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }
}

