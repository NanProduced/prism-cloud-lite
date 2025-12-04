package nan.produced.prism.device.application.domain.device;

import lombok.Getter;

@Getter
public enum DeviceAccountStatus {

    ENABLE(0),

    DISABLE(1);


    private final Integer status;

    DeviceAccountStatus(Integer status) {
        this.status = status;
    }
}
