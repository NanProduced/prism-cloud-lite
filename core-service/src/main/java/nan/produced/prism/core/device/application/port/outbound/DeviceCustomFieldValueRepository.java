package nan.produced.prism.core.device.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import nan.produced.prism.core.device.domain.customfield.DeviceCustomFieldValueEntity;

public interface DeviceCustomFieldValueRepository {

    Optional<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdAndFieldId(UUID userId, Long deviceId, Long fieldId);

    List<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdAndFieldIdIn(UUID userId, Long deviceId, List<Long> fieldIds);

    /**
     * 批量查询用户多个设备的自定义字段值
     *
     * @param userId 用户ID
     * @param deviceIds 设备ID列表
     * @return 自定义字段值列表
     */
    List<DeviceCustomFieldValueEntity> findByUserIdAndDeviceIdIn(UUID userId, List<Long> deviceIds);

    void deleteByUserIdAndDeviceIdAndFieldId(UUID userId, Long deviceId, Long fieldId);

    void deleteByUserIdAndFieldId(UUID userId, Long fieldId);

    DeviceCustomFieldValueEntity save(DeviceCustomFieldValueEntity entity);
}
