package nan.produced.prism.core.device.domain.tags;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * 设备标签复合主键
 */
@Data
public class DeviceTagMapId implements Serializable {

    @Serial
    private static final long serialVersionUID = -7626295166230395325L;

    private Long deviceId;
    private Long tagId;
}
