package nan.produced.prism.core.device.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateDeviceResp {

    private Long deviceId;

    private String deviceName;

    private String deviceAccount;

    private String devicePassword;

}
