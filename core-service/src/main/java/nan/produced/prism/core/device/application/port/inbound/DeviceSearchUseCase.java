package nan.produced.prism.core.device.application.port.inbound;

import nan.produced.prism.core.device.domain.dto.DeviceListVO;

import java.util.List;
import java.util.UUID;

/**
 * 设备查询
 *
 * @author Nan
 */
public interface DeviceSearchUseCase {

    /**
     * 查询用户所有设备
     * @param userId 用户ID
     * @return 设备列表
     */
    List<DeviceListVO> listAllUsersDevices(UUID userId);
}
