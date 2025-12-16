package nan.produced.prism.core.device.domain;

import lombok.Getter;

@Getter
public enum DeviceNetworkType {
    WIFI_AP(0, "wifi ap"),

    WIFI(1, "wifi"),

    LAN(2, "lan"),

    FOUR_G(3, "4g");

    private final Integer code;

    private final String name;

    DeviceNetworkType(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    public static DeviceNetworkType getByCode(Integer code) {
        for (DeviceNetworkType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static DeviceNetworkType getByName(String name) {
        for (DeviceNetworkType value : values()) {
            if (value.name.equals(name)) {
                return value;
            }
        }
        return null;
    }
}
