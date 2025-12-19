package nan.produced.prism.core.device.application.port.inbound;

import java.util.UUID;
import nan.produced.prism.core.device.api.dto.BatchDeviceActionDispatchReq;
import nan.produced.prism.core.device.api.dto.BatchDeviceActionDispatchResp;
import nan.produced.prism.core.device.api.dto.DeviceActionDispatchResp;
import nan.produced.prism.core.device.domain.command.DeviceActionBase;

/**
 * 设备动作（指令）下发用例（面向 SPA）
 */
public interface DeviceActionDispatchUseCase {

    DeviceActionDispatchResp dispatchSingle(UUID userId, Long deviceId, DeviceActionBase action);

    BatchDeviceActionDispatchResp dispatchBatch(UUID userId, BatchDeviceActionDispatchReq req);
}

