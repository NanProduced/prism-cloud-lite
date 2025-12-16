package nan.produced.prism.core.device.domain.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nan.produced.prism.core.device.domain.customfield.CustomFieldType;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeviceCustomFieldDefVO {

    private Long fieldId;

    private String fieldKey;

    private CustomFieldType fieldType;

    private String displayName;

    private String description;

    private String icon;

    private Boolean planTierRequired;

    private Integer sequence;

    private List<DeviceCustomFieldOptionVO> options;
}

