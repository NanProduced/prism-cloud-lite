package nan.produced.prism.device.infrastructure.internal.core;

import nan.produced.prism.device.infrastructure.internal.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * device-service → core-service 内部调用：查询设备排程（/wp-json/wp/v3/schedules 的数据源）。
 */
@FeignClient(name = "core-service", contextId = "core-schedule", path = "/internal/devices")
public interface CoreScheduleDistributionClient {

    @GetMapping("/{deviceId}/schedules")
    ResponseEntity<ApiResponse<String>> getDeviceSchedule(@PathVariable("deviceId") Long deviceId);
}

