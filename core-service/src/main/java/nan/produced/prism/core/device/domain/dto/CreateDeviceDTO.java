package nan.produced.prism.core.device.domain.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateDeviceDTO {

    private UUID userId;

    private String displayName;

    private String account;

    private String password;

    private String description;
}
