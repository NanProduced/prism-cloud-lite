package nan.produced.prism.device.infrastructure.internal.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nan.produced.prism.device.infrastructure.internal.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * core-service 内部接口调用封装（device-service）：排程分发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreScheduleDistributionService {

    private static final String EMPTY_SCHEDULE_JSON = "{\"contentsSchedule\":[],\"commandSchedule\":[]}";

    private final CoreScheduleDistributionClient coreScheduleDistributionClient;

    public String getDeviceScheduleJson(Long deviceId) {
        ResponseEntity<ApiResponse<String>> response;
        try {
            response = coreScheduleDistributionClient.getDeviceSchedule(deviceId);
        } catch (Exception e) {
            log.error("Call core-service getDeviceSchedule failed, deviceId={}", deviceId, e);
            return EMPTY_SCHEDULE_JSON;
        }
        return extractData(response, "getDeviceSchedule");
    }

    private String extractData(ResponseEntity<ApiResponse<String>> response, String apiName) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            log.warn("core-service {} http status not ok: {}", apiName, response != null ? response.getStatusCode() : null);
            return EMPTY_SCHEDULE_JSON;
        }
        ApiResponse<String> body = response.getBody();
        if (body == null) {
            log.warn("core-service {} empty body", apiName);
            return EMPTY_SCHEDULE_JSON;
        }
        String code = body.getCode();
        if (!isSuccessCode(code)) {
            log.warn("core-service {} failed: code={}, message={}", apiName, body.getCode(), body.getMessage());
            return EMPTY_SCHEDULE_JSON;
        }
        String data = body.getData();
        return data != null ? data : EMPTY_SCHEDULE_JSON;
    }

    private boolean isSuccessCode(String code) {
        return "200".equals(code) || "CORE-0000".equals(code);
    }
}

