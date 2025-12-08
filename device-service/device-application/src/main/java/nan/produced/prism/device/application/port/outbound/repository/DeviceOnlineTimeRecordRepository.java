package nan.produced.prism.device.application.port.outbound.repository;

import nan.produced.prism.device.application.dto.record.DeviceOnlineTimeRecord;

/**
 * 设备在线时长记录存储
 *
 * @author Nan
 */
public interface DeviceOnlineTimeRecordRepository {

    /**
     * 保存设备在线时长记录
     * @param deviceOnlineTimeRecord 设备在线时长记录
     */
    void saveDeviceOnlineTimeRecord(DeviceOnlineTimeRecord deviceOnlineTimeRecord);
}
