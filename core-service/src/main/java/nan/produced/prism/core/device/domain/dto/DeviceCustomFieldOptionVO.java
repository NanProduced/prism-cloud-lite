package nan.produced.prism.core.device.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeviceCustomFieldOptionVO {

    private Long optionId;

    private String optionKey;

    private String displayName;

    private String description;

    private Integer sequence;

    private Boolean active;

    private String color;
}

