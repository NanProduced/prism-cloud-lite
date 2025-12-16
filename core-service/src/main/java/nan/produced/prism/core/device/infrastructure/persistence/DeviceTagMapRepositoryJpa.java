package nan.produced.prism.core.device.infrastructure.persistence;

import nan.produced.prism.core.device.domain.tags.DeviceTagMapEntity;
import nan.produced.prism.core.device.domain.tags.DeviceTagMapId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 设备标签映射 JPA 仓库
 *
 * @author Nan
 */
@Repository
public interface DeviceTagMapRepositoryJpa extends JpaRepository<DeviceTagMapEntity, DeviceTagMapId> {

    /**
     * 根据设备ID查找关联的标签映射
     */
    List<DeviceTagMapEntity> findByDeviceId(Long deviceId);

    /**
     * 根据设备ID删除所有标签映射
     */
    void deleteByDeviceId(Long deviceId);

    /**
     * 根据标签ID删除所有设备映射
     */
    void deleteByTagId(Long tagId);

    /**
     * 检查标签是否有设备关联
     */
    boolean existsByTagId(Long tagId);

    /**
     * 根据设备ID和用户ID查找关联的标签映射（带标签信息）
     */
    @Query("SELECT m FROM DeviceTagMapEntity m JOIN FETCH m.tag WHERE m.deviceId = :deviceId AND m.userId = :userId")
    List<DeviceTagMapEntity> findByDeviceIdAndUserIdWithTag(@Param("deviceId") Long deviceId, @Param("userId") UUID userId);

    /**
     * 批量查询设备标签映射（带标签信息）
     */
    @Query("SELECT m FROM DeviceTagMapEntity m JOIN FETCH m.tag WHERE m.deviceId IN :deviceIds AND m.userId = :userId")
    List<DeviceTagMapEntity> findByDeviceIdInAndUserIdWithTag(@Param("deviceIds") List<Long> deviceIds, @Param("userId") UUID userId);
}
