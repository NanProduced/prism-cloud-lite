package nan.produced.prism.device.boot.integration.core;

import java.util.List;
import nan.produced.prism.device.boot.integration.ApiResponse;
import nan.produced.prism.device.boot.integration.core.dto.CoreDeviceProgramMediaDTO;
import nan.produced.prism.device.boot.integration.core.dto.CoreDeviceProgramDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * device-service → core-service 内部调用：查询设备节目/素材清单。
 *
 * <p>注意：</p>
 * <ul>
 *   <li>device-service 将来与 core-service 分库，因此严禁直查 core DB</li>
 *   <li>这里返回的数据由 core-service 决定，device-service 只做协议适配</li>
 * </ul>
 */
@FeignClient(name = "core-service", path = "/internal/devices")
public interface CoreProgramDistributionClient {

    @GetMapping("/{deviceId}/programs")
    ResponseEntity<ApiResponse<List<CoreDeviceProgramDTO>>> listDevicePrograms(@PathVariable("deviceId") Long deviceId);

    @GetMapping("/{deviceId}/programs/{deviceProgramId}/media")
    ResponseEntity<ApiResponse<List<CoreDeviceProgramMediaDTO>>> listDeviceProgramMedia(
            @PathVariable("deviceId") Long deviceId,
            @PathVariable("deviceProgramId") Integer deviceProgramId);
}

