package nan.produced.prism.core.device.application.port.outbound;

import nan.produced.prism.core.device.domain.DeviceEntity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeviceRepository {

    void createDevice(DeviceEntity entity);

    DeviceEntity findByDeviceId(Long deviceId);

    DeviceEntity findByDeviceIdAndUserId(Long deviceId, UUID userId);

    /**
     * 查询用户的全部设备（Lite 为个人用户场景，默认不分页）
     *
     * @param userId 用户ID
     * @return 设备列表
     */
    List<DeviceEntity> findByUserId(UUID userId);

    /**
     * 批量查询用户的设备（用于发布/批量操作的在线状态判定等）。
     */
    List<DeviceEntity> findByUserIdAndDeviceIds(UUID userId, Collection<Long> deviceIds);

    /**
     * 根据设备ID查所属用户ID
     * @param deviceId 设备ID
     * @return 用户ID
     */
    UUID findUserIdByDeviceId(Long deviceId);

    /**
     * 仅更新有值的字段和最后上报时间
     * 注意properties字段(JSON)，仅为部分JSON子结构，需要部分更新
     */
    void updateDeviceProperties(DeviceEntity entity);

    /**
     * 更新设备在线状态,最后上报时间,如果onboardingTime为空则同时插入onboardingTime
     * @param deviceId 设备ID
     * @param status 状态 (0:离线,1:在线)
     * @param time  时间
     */
    void updateStatusWithOnboarding(Long deviceId, Integer status, LocalDateTime time);

    /**
     * 更新设备在线状态,最后上报时间
     * @param deviceId 设备ID
     * @param status 状态 (0:离线,1:在线)
     * @param time  时间
     */
    void updateStatus(Long deviceId, Integer status, LocalDateTime time);
}
