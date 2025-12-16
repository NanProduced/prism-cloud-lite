package nan.produced.prism.core.device.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.common.util.TraceUtils;
import nan.produced.prism.core.device.api.dto.LinkTagsReq;
import nan.produced.prism.core.device.api.dto.OperateTagReq;
import nan.produced.prism.core.device.application.port.inbound.DeviceTagUseCase;
import nan.produced.prism.core.device.domain.dto.TagVO;
import nan.produced.prism.core.security.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 设备标签控制器
 * <p>
 * 提供标签的 CRUD 操作及设备-标签关联管理
 *
 * @author Nan
 */
@Slf4j
@Tag(name = "设备标签", description = "设备标签管理与设备-标签关联（GET/POST）")
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/devices", "/device"})
@Validated
public class DeviceTagController {

    private final DeviceTagUseCase deviceTagUseCase;

    /**
     * 获取用户标签列表
     *
     * @return 用户标签列表
     */
    @GetMapping("/tags")
    @Operation(summary = "获取当前用户的标签列表", description = "用于 TagPicker 的可选标签列表加载。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回标签列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagVO.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<List<TagVO>>> listTags() {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<TagVO> tags = deviceTagUseCase.getUserTags(userId);
        return ResponseEntity.ok(BffResponse.success(tags).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 获取单个标签详情
     *
     * @param slug 标签Slug
     * @return 标签详情
     */
    @GetMapping("/tags/{slug}")
    @Operation(summary = "获取单个标签详情", description = "根据 slug 获取当前用户的标签详情。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回标签详情",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TagVO.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<TagVO>> getTag(@PathVariable("slug") @NotBlank String slug) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        TagVO tag = deviceTagUseCase.getTagBySlug(userId, slug);
        return ResponseEntity.ok(BffResponse.success(tag).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 创建标签
     *
     * @param req 创建标签请求
     * @return 创建的标签
     */
    @PostMapping("/tags")
    @Operation(summary = "创建标签", description = "创建当前用户的新标签（slug 将由后端根据名称生成并去重）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功创建标签",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TagVO.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<TagVO>> createTag(@RequestBody @Validated OperateTagReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        TagVO tag = deviceTagUseCase.createTag(userId, req.getTagName(), req.getColor(), req.getIcon(), req.getDescription());
        return ResponseEntity.ok(BffResponse.success(tag).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 更新标签
     *
     * @param slug 标签Slug
     * @param req  更新标签请求
     * @return 更新的标签
     */
    @PostMapping("/tags/{slug}")
    @Operation(summary = "更新标签", description = "更新标签名称/颜色/图标/描述；若名称变更将重新生成 slug 并检查冲突。")
    @ApiResponse(
            responseCode = "200",
            description = "成功更新标签",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TagVO.class)))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<TagVO>> updateTag(
            @PathVariable("slug") @NotBlank String slug,
            @RequestBody @Validated OperateTagReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        TagVO tag = deviceTagUseCase.updateTag(userId, slug, req.getTagName(), req.getColor(), req.getIcon(), req.getDescription());
        return ResponseEntity.ok(BffResponse.success(tag).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 删除标签
     *
     * @param slug 标签Slug
     * @return 空响应
     */
    @PostMapping("/tags/{slug}/delete")
    @Operation(summary = "删除标签", description = "删除当前用户的标签；后端会先删除标签与设备的关联。")
    @ApiResponse(responseCode = "200", description = "成功删除标签")
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<Void>> deleteTag(@PathVariable("slug") @NotBlank String slug) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        deviceTagUseCase.deleteTag(userId, slug);
        return ResponseEntity.ok(BffResponse.<Void>success(null).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 获取设备关联的标签
     *
     * @param deviceId 设备ID
     * @return 设备关联的标签列表
     */
    @GetMapping({"/{deviceId}/tags", "/tags/device/{deviceId}"})
    @Operation(summary = "获取设备关联标签", description = "获取指定设备的标签列表（仅返回当前用户设备的数据）。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回设备标签列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagVO.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<List<TagVO>>> getDeviceTags(
            @PathVariable("deviceId") @NotNull Long deviceId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<TagVO> tags = deviceTagUseCase.getDeviceTags(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(tags).withTraceId(TraceUtils.getTraceId()));
    }

    /**
     * 关联标签到设备（覆盖，全量替换设备的标签）
     * <p>
     * 传入空列表或 null 表示清空设备的所有标签
     *
     * @param deviceId 设备ID
     * @param req      关联标签请求
     * @return 设备关联的标签列表
     */
    @PostMapping({"/{deviceId}/tags", "/tags/device/{deviceId}"})
    @Operation(summary = "更新设备标签（全量替换）", description = "以全量替换方式更新设备标签；传空列表或 null 表示清空设备的所有标签。")
    @ApiResponse(
            responseCode = "200",
            description = "成功返回更新后的设备标签列表",
            content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TagVO.class))))
    @ApiResponse(responseCode = "401", description = "CLOUD_AUTH 头缺失或无效")
    public ResponseEntity<BffResponse<List<TagVO>>> linkTags(
            @PathVariable("deviceId") @NotNull Long deviceId,
            @RequestBody @Validated LinkTagsReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        deviceTagUseCase.linkTags(deviceId, userId, req.getTags());

        // 返回更新后的设备标签列表
        List<TagVO> tags = deviceTagUseCase.getDeviceTags(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(tags).withTraceId(TraceUtils.getTraceId()));
    }
}
