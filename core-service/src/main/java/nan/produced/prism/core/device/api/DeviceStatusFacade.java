package nan.produced.prism.core.device.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 设备状态查询门面（跨模块暴露）。
 *
 * <p>用于节目发布等场景快速判断目标设备的在线情况。</p>
 */
public interface DeviceStatusFacade {

    /**
     * 返回给定设备集合中“当前在线”的设备ID列表（onlineStatus==1）。
     */
    List<Long> findOnlineDeviceIds(UUID userId, Collection<Long> deviceIds);
}

