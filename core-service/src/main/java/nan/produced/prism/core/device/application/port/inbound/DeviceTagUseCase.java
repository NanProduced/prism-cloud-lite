package nan.produced.prism.core.device.application.port.inbound;

import nan.produced.prism.core.device.domain.dto.TagVO;

import java.util.List;
import java.util.UUID;

public interface DeviceTagUseCase {

    /**
     * 创建标签
     *
     * @param userId      用户Id
     * @param tagName     标签名称
     * @param color       颜色
     * @param icon        图标
     * @param description 描述
     * @return 创建的标签
     */
    TagVO createTag(UUID userId, String tagName, String color, String icon, String description);

    /**
     * 更新标签
     *
     * @param userId      用户ID
     * @param slug        标签Slug
     * @param tagName     标签名称
     * @param color       颜色
     * @param icon        图标
     * @param description 描述
     * @return 更新的标签
     */
    TagVO updateTag(UUID userId, String slug, String tagName, String color, String icon, String description);

    /**
     * 删除标签
     *
     * @param userId 用户ID
     * @param tag    标签Slug
     */
    void deleteTag(UUID userId, String tag);

    /**
     * 通过Slug获取标签
     *
     * @param userId 用户ID
     * @param slug   标签Slug
     * @return 标签
     */
    TagVO getTagBySlug(UUID userId, String slug);

    /**
     * 获取用户标签列表
     * @param userId 用户ID
     * @return 用户标签列表
     */
    List<TagVO> getUserTags(UUID userId);

    /**
     * 获取设备关联的标签
     *
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @return 设备关联的标签
     */
    List<TagVO> getDeviceTags(UUID userId, Long deviceId);

    /**
     * 批量关联标签(覆盖，全量替换)
     *
     * @param deviceId 设备ID
     * @param userId    用户Id
     * @param tags     标签Slug列表
     */
    void linkTags(Long deviceId, UUID userId, List<String> tags);


}
