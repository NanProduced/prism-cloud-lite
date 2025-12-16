package nan.produced.prism.core.device.application.port.inbound;

import nan.produced.prism.core.device.api.dto.DeviceDetailResp;
import nan.produced.prism.core.device.api.dto.FilterDeviceReq;
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

    /**
     * 查询设备详情
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @return 设备详情
     */
    DeviceDetailResp getDeviceDetail(UUID userId, Long deviceId);

    /**
     * 设备筛选查询（服务端过滤，条件为交集）
     * <p>
     * 说明：Lite 个人用户场景设备数量有限，前端默认全量加载后本地过滤；该接口作为开放 API/后续扩展预留。
     *
     * @param userId 用户ID
     * @param req    筛选参数
     * @return 筛选后的设备列表（不分页）
     */
    List<DeviceListVO> filterDevices(UUID userId, FilterDeviceReq req);
}
