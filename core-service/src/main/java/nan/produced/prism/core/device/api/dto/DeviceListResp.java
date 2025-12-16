package nan.produced.prism.core.device.api.dto;

import lombok.Data;
import nan.produced.prism.core.device.domain.dto.DeviceListVO;

import java.util.List;

@Data
public class DeviceListResp {

    private Long total;

    private Long online;

    private Long offline;

    private List<DeviceListVO> devices;
}
