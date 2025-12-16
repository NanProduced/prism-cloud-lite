package nan.produced.prism.core.device.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;
import nan.produced.prism.core.device.domain.customfield.CustomFieldType;

@Data
public class CreateCustomFieldReq {

    @NotBlank
    @Size(max = 128)
    private String displayName;

    @NotNull
    private CustomFieldType fieldType;

    /**
     * 可选：若传入将被 slug 化；为空则由 displayName 生成。
     */
    @Size(max = 128)
    private String fieldKey;

    @Size(max = 512)
    private String description;

    @Size(max = 64)
    private String icon;

    /**
     * 是否为 Pro-only 列。
     * Free 用户不允许创建 true。
     */
    private Boolean planTierRequired = false;

    private Integer sequence;

    @Valid
    private List<Option> options;

    @Data
    public static class Option {

        @Size(max = 128)
        private String optionKey;

        @NotBlank
        @Size(max = 128)
        private String displayName;

        @Size(max = 512)
        private String description;

        private Integer sequence;

        private Boolean active = true;

        @Size(max = 32)
        private String color;
    }
}

