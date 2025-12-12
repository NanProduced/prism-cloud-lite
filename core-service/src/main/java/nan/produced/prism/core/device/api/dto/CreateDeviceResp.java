package nan.produced.prism.core.device.api.dto;

import lombok.Data;

@Data
public class CreateDeviceResp {

    private Long deviceId;

    private String deviceName;

    private String deviceAccount;

    private String devicePassword;

}
