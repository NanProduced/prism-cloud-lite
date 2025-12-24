package nan.produced.prism.core.media.infrastructure.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.media.application.dto.*;
import nan.produced.prism.core.media.application.service.MediaLibraryService;
import nan.produced.prism.core.security.api.CloudAuthContext;
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
@Tag(name = "素材库", description = "素材库目录浏览与管理（面向 SPA，经由 Gateway 访问）")
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
    @Operation(
        summary = "获取素材库配额与使用统计",
        description = """
            返回当前用户在素材库（Media Library）下的配额（quota）与使用量（usage）统计。

            - `quotaBytes` 来自订阅套餐；`usedBytes` 为当前已占用；
            - `bytesByKind` 统计 image/video/document/other 的字节数；
            - `counts` 统计各类型文件数量与 folders 数量。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功返回使用统计",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MediaLibraryUsageResponse.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
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
    @Operation(
        summary = "列出目录节点（文件夹优先）",
        description = """
            列出指定目录下的节点（文件夹 + 素材），并保证“文件夹优先”。

            - `parentId` 不传/null 表示根目录；
            - `q` 为名称模糊匹配（当前主要用于服务端补充筛选能力）；
            - `sort` 支持：`updatedAt`（默认）、`name`、`size`；
            - `limit` 最大 500；
            - `cursor` 为 offset 字符串：使用上一次响应的 `nextCursor` 继续翻页；非法 cursor 会被视为 0。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功返回节点列表（含 nextCursor）",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MediaLibraryNodesResponse.class)))
    @ApiResponse(responseCode = "400", description = "请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<MediaLibraryNodesResponse>> listNodes(
            @Parameter(description = "父文件夹ID（不传/为 null 表示根目录）")
            @RequestParam(value = "parentId", required = false) String parentId,
            @Parameter(description = "名称搜索关键字（可选）")
            @RequestParam(value = "q", required = false) String q,
            @Parameter(description = "排序：updatedAt|name|size（默认 updatedAt）")
            @RequestParam(value = "sort", required = false) String sort,
            @Parameter(description = "每页数量（最大 500）")
            @RequestParam(value = "limit", required = false) Integer limit,
            @Parameter(description = "游标（offset 字符串），使用上一次响应的 nextCursor 继续分页")
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
    @Operation(
        summary = "列出全部文件夹（用于上传目标 Tree）",
        description = "返回当前用户素材库中所有文件夹节点（不含素材）。用于上传弹窗中的目录树渲染。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回文件夹列表",
        content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MediaNodeDto.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
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
    @Operation(
        summary = "创建文件夹",
        description = """
            在指定 parentId 下创建文件夹。

            - `parentId` 不传/null 表示创建在根目录；
            - `name` 必填，长度限制 64；
            - 创建成功返回文件夹节点（type=folder）。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功创建文件夹",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MediaNodeDto.class)))
    @ApiResponse(responseCode = "400", description = "文件夹名称不合法或请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "父文件夹不存在或无权访问")
    public ResponseEntity<BffResponse<MediaNodeDto>> createFolder(@RequestBody @Validated CreateFolderRequest request) {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        var folder = mediaLibraryService.createFolder(userId, request.getParentId(), request.getName());
        return ResponseEntity.ok(BffResponse.success(folder).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 重命名（文件夹/素材）
     */
    @PostMapping("/nodes/{id}/rename")
    @Operation(
        summary = "重命名节点（文件夹/素材）",
        description = "重命名指定节点（文件夹或素材）。对素材：更新 title；对文件夹：更新名称。")
    @ApiResponse(
        responseCode = "200",
        description = "成功返回更新后的节点信息",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MediaNodeDto.class)))
    @ApiResponse(responseCode = "400", description = "请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节点不存在或无权访问")
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
    @Operation(
        summary = "移动节点（文件夹/素材）",
        description = """
            将一组节点移动到目标目录（不重新上传）。

            - `targetParentId` 不传/null 表示移动到根目录；
            - 文件夹不允许移动到自身或其子目录（会返回 400）；
            - 返回 moved 表示成功移动的节点数量。
            """)
    @ApiResponse(
        responseCode = "200",
        description = "成功移动节点",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = MoveNodesResponse.class)))
    @ApiResponse(responseCode = "400", description = "移动目标不合法或请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节点/目标文件夹不存在或无权访问")
    public ResponseEntity<BffResponse<MoveNodesResponse>> moveNodes(@RequestBody @Validated MoveNodesRequest request) {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        var response = mediaLibraryService.moveNodes(userId, request.getNodeIds(), request.getTargetParentId());
        return ResponseEntity.ok(BffResponse.success(response).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 删除（文件夹/素材）
     */
    @PostMapping("/nodes/{id}/delete")
    @Operation(
        summary = "删除节点（文件夹/素材）",
        description = """
            删除指定节点：
            - 若为文件夹：仅允许删除空文件夹（非空会返回 400）；
            - 若为素材：删除素材记录，并减少引用文件的 refCount；当 refCount 变为 0 时会同步扣减存储统计。
            """)
    @ApiResponse(responseCode = "200", description = "成功删除")
    @ApiResponse(responseCode = "400", description = "文件夹非空或请求参数不合法")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    @ApiResponse(responseCode = "404", description = "节点不存在或无权访问")
    public ResponseEntity<BffResponse<Void>> deleteNode(@PathVariable("id") String id) {
        var user = CloudAuthContext.getCurrentUser();
        UUID userId = UUID.fromString(user.userUuid());
        mediaLibraryService.deleteNode(userId, id);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }
}
