package nan.produced.prism.core.device.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class UpdateCustomFieldReq {

    @Size(max = 128)
    private String displayName;

    @Size(max = 512)
    private String description;

    @Size(max = 64)
    private String icon;

    private Boolean planTierRequired;

    private Integer sequence;

    /**
     * 传入则视为全量替换 options（仅对 SELECT/MULTI_SELECT 有意义）。
     */
    @Valid
    private List<CreateCustomFieldReq.Option> options;
}

