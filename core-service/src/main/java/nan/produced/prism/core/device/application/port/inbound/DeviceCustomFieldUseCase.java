package nan.produced.prism.core.device.application.port.inbound;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import nan.produced.prism.core.device.domain.customfield.CustomFieldType;
import nan.produced.prism.core.device.domain.dto.DeviceCustomFieldDefVO;

public interface DeviceCustomFieldUseCase {

    List<DeviceCustomFieldDefVO> listCustomFieldDefs(UUID userId);

    DeviceCustomFieldDefVO createCustomFieldDef(
            UUID userId,
            String tier,
            String displayName,
            CustomFieldType fieldType,
            String fieldKey,
            String description,
            String icon,
            Boolean planTierRequired,
            Integer sequence,
            List<DeviceCustomFieldOptionSpec> options);

    DeviceCustomFieldDefVO updateCustomFieldDef(
            UUID userId,
            String tier,
            Long fieldId,
            String displayName,
            String description,
            String icon,
            Boolean planTierRequired,
            Integer sequence,
            List<DeviceCustomFieldOptionSpec> options);

    void deleteCustomFieldDef(UUID userId, String tier, Long fieldId);

    Map<String, Object> patchDeviceCustomFieldValues(UUID userId, String tier, Long deviceId, Map<Long, Object> values);
}

