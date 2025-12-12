package nan.produced.prism.core.device.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDeviceReq {

    @Max(64)
    @NotNull
    private String displayName;

    @Min(8)
    @Max(64)
    @NotNull
    private String account;

    @Min(12)
    @Max(64)
    @NotNull
    private String password;

    @Max(128)
    private String description;
}
