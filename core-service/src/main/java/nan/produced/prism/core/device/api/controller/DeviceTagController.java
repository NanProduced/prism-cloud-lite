package nan.produced.prism.core.device.api.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.response.BffResponse;
import nan.produced.prism.core.device.api.dto.LinkTagsReq;
import nan.produced.prism.core.device.api.dto.OperateTagReq;
import nan.produced.prism.core.device.application.port.inbound.DeviceTagUseCase;
import nan.produced.prism.core.device.domain.dto.TagVO;
import nan.produced.prism.core.security.CloudAuthContext;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 设备标签控制器
 * <p>
 * 提供标签的 CRUD 操作及设备-标签关联管理
 *
 * @author Nan
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/device/tags")
@Validated
public class DeviceTagController {

    private final DeviceTagUseCase deviceTagUseCase;

    /**
     * 获取用户标签列表
     *
     * @return 用户标签列表
     */
    @GetMapping
    public ResponseEntity<BffResponse<List<TagVO>>> listTags() {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<TagVO> tags = deviceTagUseCase.getUserTags(userId);
        return ResponseEntity.ok(BffResponse.success(tags));
    }

    /**
     * 获取单个标签详情
     *
     * @param slug 标签Slug
     * @return 标签详情
     */
    @GetMapping("/{slug}")
    public ResponseEntity<BffResponse<TagVO>> getTag(@PathVariable("slug") @NotBlank String slug) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        TagVO tag = deviceTagUseCase.getTagBySlug(userId, slug);
        return ResponseEntity.ok(BffResponse.success(tag));
    }

    /**
     * 创建标签
     *
     * @param req 创建标签请求
     * @return 创建的标签
     */
    @PostMapping
    public ResponseEntity<BffResponse<TagVO>> createTag(@RequestBody @Validated OperateTagReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        TagVO tag = deviceTagUseCase.createTag(userId, req.getTagName(), req.getColor(), req.getIcon(), req.getDescription());
        return ResponseEntity.ok(BffResponse.success(tag));
    }

    /**
     * 更新标签
     *
     * @param slug 标签Slug
     * @param req  更新标签请求
     * @return 更新的标签
     */
    @PostMapping("/{slug}")
    public ResponseEntity<BffResponse<TagVO>> updateTag(
            @PathVariable("slug") @NotBlank String slug,
            @RequestBody @Validated OperateTagReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        TagVO tag = deviceTagUseCase.updateTag(userId, slug, req.getTagName(), req.getColor(), req.getIcon(), req.getDescription());
        return ResponseEntity.ok(BffResponse.success(tag));
    }

    /**
     * 删除标签
     *
     * @param slug 标签Slug
     * @return 空响应
     */
    @PostMapping("/{slug}/delete")
    public ResponseEntity<BffResponse<Void>> deleteTag(@PathVariable("slug") @NotBlank String slug) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        deviceTagUseCase.deleteTag(userId, slug);
        return ResponseEntity.ok(BffResponse.success(null));
    }

    /**
     * 获取设备关联的标签
     *
     * @param deviceId 设备ID
     * @return 设备关联的标签列表
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<BffResponse<List<TagVO>>> getDeviceTags(
            @PathVariable("deviceId") @NotNull Long deviceId) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        List<TagVO> tags = deviceTagUseCase.getDeviceTags(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(tags));
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
    @PostMapping("/device/{deviceId}")
    public ResponseEntity<BffResponse<List<TagVO>>> linkTags(
            @PathVariable("deviceId") @NotNull Long deviceId,
            @RequestBody @Validated LinkTagsReq req) {
        UUID userId = UUID.fromString(CloudAuthContext.getCurrentUser().userUuid());
        deviceTagUseCase.linkTags(deviceId, userId, req.getTags());

        // 返回更新后的设备标签列表
        List<TagVO> tags = deviceTagUseCase.getDeviceTags(userId, deviceId);
        return ResponseEntity.ok(BffResponse.success(tags));
    }
}
