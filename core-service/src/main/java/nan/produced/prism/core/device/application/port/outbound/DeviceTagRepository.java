package nan.produced.prism.core.device.application.port.outbound;

import nan.produced.prism.core.device.domain.tags.DeviceTagEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 设备标签仓库端口
 * <p>
 * 六边形架构中的出站端口，定义设备标签的数据访问接口
 *
 * @author Nan
 */
public interface DeviceTagRepository {

    /**
     * 保存标签
     *
     * @param tag 标签实体
     * @return 保存后的标签实体
     */
    DeviceTagEntity save(DeviceTagEntity tag);

    /**
     * 根据用户ID和Slug查找标签
     *
     * @param userId 用户ID
     * @param slug   标签Slug
     * @return 标签实体
     */
    Optional<DeviceTagEntity> findByUserIdAndSlug(UUID userId, String slug);

    /**
     * 检查用户是否存在指定Slug的标签
     *
     * @param userId 用户ID
     * @param slug   标签Slug
     * @return 存在返回true
     */
    boolean existsByUserIdAndSlug(UUID userId, String slug);

    /**
     * 根据用户ID查找所有标签
     *
     * @param userId 用户ID
     * @return 标签列表
     */
    List<DeviceTagEntity> findByUserId(UUID userId);

    /**
     * 根据用户ID和标签ID查找标签
     *
     * @param tagId  标签ID
     * @param userId 用户ID
     * @return 标签实体
     */
    Optional<DeviceTagEntity> findByTagIdAndUserId(Long tagId, UUID userId);

    /**
     * 根据用户ID和Slug列表批量查找标签
     *
     * @param userId 用户ID
     * @param slugs  Slug列表
     * @return 标签列表
     */
    List<DeviceTagEntity> findByUserIdAndSlugIn(UUID userId, List<String> slugs);

    /**
     * 删除用户的指定标签
     *
     * @param userId 用户ID
     * @param slug   标签Slug
     */
    void deleteByUserIdAndSlug(UUID userId, String slug);

    /**
     * 删除标签（通过实体）
     *
     * @param tag 标签实体
     */
    void delete(DeviceTagEntity tag);

    /**
     * 获取设备关联的标签列表
     *
     * @param deviceId 设备ID
     * @param userId   用户ID
     * @return 标签列表
     */
    List<DeviceTagEntity> findByDeviceId(Long deviceId, UUID userId);

    /**
     * 替换设备的标签关联（全量覆盖）
     *
     * @param deviceId 设备ID
     * @param userId   用户ID
     * @param tagIds   标签ID列表
     */
    void replaceDeviceTags(Long deviceId, UUID userId, List<Long> tagIds);

    /**
     * 删除标签的所有设备关联
     *
     * @param tagId 标签ID
     */
    void deleteTagMappings(Long tagId);

    /**
     * 检查标签是否有设备关联
     *
     * @param tagId 标签ID
     * @return 有关联返回true
     */
    boolean hasDeviceMappings(Long tagId);
}
