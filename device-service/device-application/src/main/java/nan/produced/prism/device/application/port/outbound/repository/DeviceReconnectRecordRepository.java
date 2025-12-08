package nan.produced.prism.device.application.port.outbound.repository;

import nan.produced.prism.device.application.dto.record.DeviceReconnectRecord;

/**
 * 设备重连记录存储接口
 *
 * @author Nan
 */
public interface DeviceReconnectRecordRepository {

    /**
     * 保存设备重连记录
     * @param deviceReconnectRecord 记录
     */
    void saveReconnectRecord(DeviceReconnectRecord deviceReconnectRecord);
}
