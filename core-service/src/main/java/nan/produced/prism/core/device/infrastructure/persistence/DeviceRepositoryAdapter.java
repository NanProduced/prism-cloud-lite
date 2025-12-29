package nan.produced.prism.core.device.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.core.common.exception.BizException;
import nan.produced.prism.core.common.exception.ErrorCode;
import nan.produced.prism.core.device.application.port.outbound.DeviceRepository;
import nan.produced.prism.core.device.domain.DeviceEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 设备数据仓库适配器
 * 实现 {@link DeviceRepository} 出站端口
 * 作为六边形架构中的适配器，负责将域模型的仓库接口与底层的持久化实现（Spring Data JPA）相连接
 * <p>
 * 职责：
 * 1. 将业务操作转换为数据库操作
 * 2. 提供事务管理
 * 3. 处理 DeviceEntity 的创建、更新等操作
 *
 * @author Nan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceRepositoryAdapter implements DeviceRepository {

    /**
     * Spring Data JPA 仓库，用于设备数据的 CRUD 操作
     */
    private final DeviceRepositoryJpa deviceRepositoryJpa;

    /**
     * 创建新设备
     * <p>
     * 流程：
     * 1. 接收 DeviceEntity
     * 2. 设置创建时间为当前时间（如果未设置）
     * 3. 保存到数据库
     *
     * @param entity 设备实体（包含设备基本信息）
     */
    @Override
    public void createDevice(DeviceEntity entity) {
        if (entity == null) {
            log.warn("DeviceRepositoryAdapter - 尝试创建空设备");
            return;
        }

        // 设置创建时间（如果未设置）
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(OffsetDateTime.now(ZoneOffset.UTC));
        }

        try {
            deviceRepositoryJpa.save(entity);
            log.info("DeviceRepositoryAdapter - 设备创建成功: deviceId={}", entity.getDeviceId());
        } catch (Exception e) {
            log.error("DeviceRepositoryAdapter - 设备创建失败: deviceId={}", entity.getDeviceId(), e);
            throw new BizException(ErrorCode.DEVICE_CREATED_FAILED, "设备创建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public UUID findUserIdByDeviceId(Long deviceId) {
        Optional<UUID> userIdByDeviceId = deviceRepositoryJpa.findUserIdByDeviceId(deviceId);
        return userIdByDeviceId.orElse(null);
    }

    @Override
    public List<DeviceEntity> findByUserId(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return deviceRepositoryJpa.findByUserIdOrderByCreateTimeDesc(userId);
    }

    @Override
    public List<DeviceEntity> findByUserIdAndDeviceIds(UUID userId, Collection<Long> deviceIds) {
        if (userId == null || deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        return deviceRepositoryJpa.findByUserIdAndDeviceIdIn(userId, deviceIds);
    }

    /**
     * 更新设备属性
     *
     * @param entity 设备实体（包含要更新的属性，可能是部分）
     */
    @Override
    @Transactional
    public void updateDeviceProperties(DeviceEntity entity) {
        if (entity == null || entity.getDeviceId() == null) {
            log.warn("DeviceRepositoryAdapter - 尝试更新空设备或无效设备ID");
            return;
        }
        try {
            deviceRepositoryJpa.save(entity);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEVICE_UPDATED_FAILED, "设备属性更新失败: " + e.getMessage(), e);
        }
    }

    @Override
    public int updateLastReportTimeIfNewer(Long deviceId, OffsetDateTime time) {
        if (deviceId == null || time == null) {
            return 0;
        }
        try {
            return deviceRepositoryJpa.updateLastReportTimeIfNewer(deviceId, time);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEVICE_UPDATED_FAILED, "设备最后上报时间更新失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新设备在线状态和最后上报时间
     * 如果设备尚未记录 onboarding 时间，则同时设置为当前时间
     * <p>
     * 业务说明：
     * - 用于处理设备首次上线
     * - 记录设备首次连接时间（onboardingTime）
     * - 后续在线状态变化使用 updateStatus 方法
     * <p>
     * 流程：
     * 1. 检查设备是否已有 onboardingTime
     * 2. 如果没有，设置为 time
     * 3. 更新在线状态和最后上报时间
     *
     * @param deviceId 设备ID
     * @param status 状态 (0:离线, 1:在线)
     * @param time 当前时间（作为最后上报时间，和潜在的 onboardingTime）
     */
    @Override
    public void updateStatusWithOnboarding(Long deviceId, Integer status, OffsetDateTime time) {
        if (deviceId == null) {
            return;
        }

        try {
            deviceRepositoryJpa.updateStatusWithOnboarding(deviceId, status, time, time);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEVICE_UPDATED_FAILED, "设备上线状态更新失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新设备在线状态和最后上报时间
     * <p>
     * 业务说明：
     * - 用于后续的在线状态变化
     * - 仅更新状态和上报时间，不影响 onboardingTime
     * - 可用于处理设备上线/离线状态变化
     * <p>
     * 流程：
     * 1. 更新在线状态
     * 2. 更新最后上报时间
     *
     * @param deviceId 设备ID
     * @param status 状态 (0:离线, 1:在线)
     * @param time 最后上报时间
     */
    @Override
    public void updateStatus(Long deviceId, Integer status, OffsetDateTime time) {
        if (deviceId == null) {
            return;
        }

        try {
            deviceRepositoryJpa.updateStatus(deviceId, status, time);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEVICE_UPDATED_FAILED, "设备在线状态更新失败: " + e.getMessage(), e);
        }
    }

    /**
     * 【扩展方法】根据设备 ID 获取设备信息
     * 虽然不在 DeviceRepository 接口中定义，但作为常用操作提供
     *
     * @param deviceId 设备ID
     * @return 设备实体
     */
    @Override
    public DeviceEntity findByDeviceId(Long deviceId) {
        return deviceRepositoryJpa.findByDeviceId(deviceId);
    }

    /**
     * 【扩展方法】根据设备 ID 和用户
     * @param deviceId 设备ID
     * @param userId 用户ID
     * @return 设备实体
     */
    @Override
    public DeviceEntity findByDeviceIdAndUserId(Long deviceId, UUID userId) {
        return deviceRepositoryJpa.findByDeviceIdAndUserId(deviceId, userId);
    }

    /**
     * 【扩展方法】删除设备
     * 虽然不在 DeviceRepository 接口中定义，但作为常用操作提供
     *
     * @param deviceId 设备ID
     */
    public void deleteDevice(Long deviceId) {
        try {
            deviceRepositoryJpa.deleteById(deviceId);
            log.info("DeviceRepositoryAdapter - 设备已删除: deviceId={}", deviceId);
        } catch (Exception e) {
            throw new BizException(ErrorCode.DEVICE_UPDATED_FAILED, "设备删除失败: " + e.getMessage(), e);
        }
    }
}
